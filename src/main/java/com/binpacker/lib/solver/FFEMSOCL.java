package com.binpacker.lib.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;

import com.binpacker.lib.ocl.KernelUtils;
import com.binpacker.lib.solver.common.PlacementUtils;
import com.binpacker.lib.solver.common.SolverProperties;

import org.jocl.*;
import static org.jocl.CL.*;

import com.binpacker.lib.ocl.OpenCLDevice;

public class FFEMSOCL implements SolverInterface {

	private String firstFitKernelSource;
	private String spaceCollisionKernelSource;

	private Bin binTemplate;
	private boolean growingBin;
	private String growAxis;

	// OpenCL resources
	private cl_context clContext;
	private cl_command_queue clQueue;
	private cl_program clProgram;
	private cl_kernel firstFitKernel;

	// Optimization: Reuse buffers
	private cl_mem inputBuffer;
	private cl_mem outputBuffer;
	private int currentBufferCapacity = 0;

	@Override
	public void init(SolverProperties properties) {
		this.binTemplate = properties.bin;
		this.growingBin = properties.growingBin;
		this.growAxis = properties.growAxis;
		this.firstFitKernelSource = KernelUtils.loadKernelSource("firstfit_rotate.cl");
		this.spaceCollisionKernelSource = KernelUtils.loadKernelSource("box_collides_with_space.cl");

		initOpenCL(properties.openCLDevice);
	}

	private void initOpenCL(OpenCLDevice preference) {
		// Enable exceptions
		CL.setExceptionsEnabled(true);

		int platformIndex = 0;
		int deviceIndex = 0;

		if (preference != null) {
			platformIndex = preference.platformIndex;
			deviceIndex = preference.deviceIndex;
		}

		// 1. Get platform
		int[] numPlatformsArray = new int[1];
		clGetPlatformIDs(0, null, numPlatformsArray);
		int numPlatforms = numPlatformsArray[0];
		if (numPlatforms == 0) {
			throw new RuntimeException("No OpenCL platforms found");
		}

		if (platformIndex >= numPlatforms) {
			throw new RuntimeException("Invalid OpenCL platform index: " + platformIndex);
		}

		cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
		clGetPlatformIDs(platforms.length, platforms, null);
		cl_platform_id platform = platforms[platformIndex];

		// 2. Get device
		int[] numDevicesArray = new int[1];
		clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, null, numDevicesArray);
		int numDevices = numDevicesArray[0];
		if (numDevices == 0) {
			throw new RuntimeException("No OpenCL devices found on platform " + platformIndex);
		}

		if (deviceIndex >= numDevices) {
			throw new RuntimeException("Invalid OpenCL device index: " + deviceIndex);
		}

		cl_device_id[] devices = new cl_device_id[numDevices];
		clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, numDevices, devices, null);
		cl_device_id device = devices[deviceIndex];

		// 3. Create context
		cl_context_properties contextProperties = new cl_context_properties();
		contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
		clContext = clCreateContext(contextProperties, 1, new cl_device_id[] { device }, null, null, null);

		// 4. Create command queue
		// Use default properties (0) or CL_QUEUE_PROFILING_ENABLE if needed
		cl_queue_properties queueProperties = new cl_queue_properties();
		clQueue = clCreateCommandQueueWithProperties(clContext, device, queueProperties, null);

		// 5. Create Program
		clProgram = clCreateProgramWithSource(clContext, 1, new String[] { firstFitKernelSource }, null, null);
		clBuildProgram(clProgram, 0, null, null, null, null);

		// 6. Create Kernel
		firstFitKernel = clCreateKernel(clProgram, "firstfit_rotate", null);
	}

	@Override
	public List<List<Box>> solve(List<Box> boxes) {
		List<Bin> activeBins = new ArrayList<>();
		List<List<Box>> result = new ArrayList<>();

		if (growingBin) {
			switch (growAxis) {
				case "x":
					binTemplate.w = Integer.MAX_VALUE;
					break;
				case "y":
					binTemplate.h = Integer.MAX_VALUE;
					break;
				case "z":
					binTemplate.d = Integer.MAX_VALUE;
					break;
				default:
					System.err.println("Invalid growAxis specified: " + growAxis);
					binTemplate.h = Integer.MAX_VALUE;
					break;
			}
		}

		activeBins.add(new Bin(0, binTemplate.w, binTemplate.h, binTemplate.d));

		for (Box box : boxes) {
			// System.out.println("Placing box " + box);
			boolean placed = false;
			for (Bin bin : activeBins) {

				// Use OpenCL to find best fit
				BoxWithIndex fit = findFit(box, bin.freeSpaces);

				if (fit != null) {
					PlacementUtils.placeBoxBSP(fit.box, bin, fit.index);
					// pruneCollidingSpaces(fit.box, bin);
					placed = true;
					break;
				}
			}

			if (!placed) {
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d);
				activeBins.add(newBin);
				// Still use CPU for single check on new bin, or just reuse finding
				// Since new bin usually has 1 free space (the whole bin), CPU is fast enough,
				// but consistency is nice.
				// However, new bin has 1 space: (0,0,0, w,h,d).

				Box fitBox = PlacementUtils.findFit(box, newBin.freeSpaces.get(0));
				if (fitBox != null) {
					PlacementUtils.placeBoxBSP(fitBox, newBin, 0);
				} else {
					System.err.println("Box too big for bin: " + box);
				}
			}

		}

		if (growingBin) {
			switch (growAxis) {
				case "x":
					float maxX = 0;
					for (Box placedBox : activeBins.get(0).boxes) {
						maxX = Math.max(maxX, placedBox.position.x + placedBox.size.x);
					}
					activeBins.get(0).w = maxX;
					break;
				case "y":
					float maxY = 0;
					for (Box placedBox : activeBins.get(0).boxes) {
						maxY = Math.max(maxY, placedBox.position.y + placedBox.size.y);
					}
					activeBins.get(0).h = maxY;
					break;
				case "z":
					float maxZ = 0;
					for (Box placedBox : activeBins.get(0).boxes) {
						maxZ = Math.max(maxZ, placedBox.position.z + placedBox.size.z);
					}
					activeBins.get(0).d = maxZ;
					break;
				default:
					System.err.println("Invalid growAxis specified for final bin sizing: " + growAxis);
					break;
			}
		}

		for (Bin bin : activeBins) {
			result.add(bin.boxes);
		}

		// Clean up? Not really possible here unless we have a close method.
		// For now let JVM/OS clean up on exit.

		// clReleaseKernel(firstFitKernel);
		// clReleaseProgram(clProgram);
		// clReleaseCommandQueue(clQueue);
		// clReleaseContext(clContext);

		return result;
	}

	private static class BoxWithIndex {
		Box box;
		int index;

		public BoxWithIndex(Box box, int index) {
			this.box = box;
			this.index = index;
		}
	}

	private BoxWithIndex findFit(Box box, List<Space> spaces) {
		if (spaces.isEmpty())
			return null;

		int numSpaces = spaces.size();
		float[] spaceData = new float[numSpaces * 3]; // w, h, d
		for (int i = 0; i < numSpaces; i++) {
			Space s = spaces.get(i);
			spaceData[i * 3 + 0] = s.w;
			spaceData[i * 3 + 1] = s.h;
			spaceData[i * 3 + 2] = s.d;
		}

		// Resize buffers if needed
		if (numSpaces > currentBufferCapacity) {
			if (inputBuffer != null) {
				clReleaseMemObject(inputBuffer);
			}
			if (outputBuffer != null) {
				clReleaseMemObject(outputBuffer);
			}

			// Grow by 100% to avoid frequent re-allocations
			currentBufferCapacity = (int) (numSpaces * 2);
			if (currentBufferCapacity < numSpaces) {
				currentBufferCapacity = numSpaces;
			}

			inputBuffer = clCreateBuffer(clContext, CL_MEM_READ_ONLY,
					(long) Sizeof.cl_float * currentBufferCapacity * 3, null, null);

			outputBuffer = clCreateBuffer(clContext, CL_MEM_READ_WRITE,
					(long) Sizeof.cl_float * currentBufferCapacity, null, null);
		}

		// Update data
		clEnqueueWriteBuffer(clQueue, inputBuffer, CL_TRUE, 0,
				(long) Sizeof.cl_float * spaceData.length, Pointer.to(spaceData), 0, null, null);

		// Set args
		clSetKernelArg(firstFitKernel, 0, Sizeof.cl_float, Pointer.to(new float[] { box.size.x }));
		clSetKernelArg(firstFitKernel, 1, Sizeof.cl_float, Pointer.to(new float[] { box.size.y }));
		clSetKernelArg(firstFitKernel, 2, Sizeof.cl_float, Pointer.to(new float[] { box.size.z }));
		clSetKernelArg(firstFitKernel, 3, Sizeof.cl_mem, Pointer.to(inputBuffer));
		clSetKernelArg(firstFitKernel, 4, Sizeof.cl_mem, Pointer.to(outputBuffer));

		// Run
		long[] globalWorkSize = new long[] { numSpaces };
		clEnqueueNDRangeKernel(clQueue, firstFitKernel, 1, null, globalWorkSize, null, 0, null, null);

		// Read back
		float[] results = new float[numSpaces];
		clEnqueueReadBuffer(clQueue, outputBuffer, CL_TRUE, 0,
				(long) Sizeof.cl_float * results.length, Pointer.to(results), 0, null, null);

		// System.out.println("Results: " + Arrays.toString(results));
		for (int i = 0; i < numSpaces; i++) {
			if (results[i] > 0) {
				return new BoxWithIndex(box, i);
			}
		}

		return null;
	}

	private float calculateScore(Box box, Space space) {
		// Add a component for distance from origin (smaller x, y, z is better)
		// Assuming space.x, space.y, space.z are non-negative.
		float distanceScore = space.x + space.y + space.z;

		return distanceScore;

	}

}
