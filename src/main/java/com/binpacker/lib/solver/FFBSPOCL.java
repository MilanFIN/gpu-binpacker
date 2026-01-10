package com.binpacker.lib.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;

import com.binpacker.lib.ocl.KernelUtils;
import com.binpacker.lib.solver.common.Placement;
import com.binpacker.lib.solver.common.SolverProperties;
import com.binpacker.lib.solver.common.ocl.OCLCommon;
import com.binpacker.lib.solver.common.ocl.GPUBinState;

import org.jocl.*;
import static org.jocl.CL.*;

import com.binpacker.lib.ocl.OpenCLDevice;

public class FFBSPOCL implements SolverInterface {

	private String firstFitKernelSource;

	private Bin binTemplate;
	private boolean growingBin;
	private String growAxis;

	// OpenCL resources
	private OCLCommon ocl = new OCLCommon();
	private cl_kernel firstFitKernel;

	// Map to track GPU resources for each bin
	private Map<Bin, GPUBinState> binStates = new HashMap<>();

	@Override
	public void init(SolverProperties properties) {
		this.binTemplate = properties.bin;
		this.growingBin = properties.growingBin;
		this.growAxis = properties.growAxis;
		this.firstFitKernelSource = KernelUtils.loadKernelSource("firstfit.cl");

		initOpenCL(properties.openCLDevice);
	}

	private void initOpenCL(OpenCLDevice preference) {
		ocl.init(firstFitKernelSource, preference);
		firstFitKernel = clCreateKernel(ocl.clProgram, "firstfit", null);
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

		// Initialize first bin
		Bin firstBin = new Bin(0, binTemplate.w, binTemplate.h, binTemplate.d);
		activeBins.add(firstBin);
		initBinState(firstBin);

		for (Box box : boxes) {
			boolean placed = false;
			for (Bin bin : activeBins) {
				// Use OpenCL to find best fit
				Placement fit = findFit(box, bin);

				if (fit != null) {
					// Use BSP placement (no pruning needed)
					placeBoxGPU(box, bin, fit.spaceIndex);

					// No pruning or utilCounter needed for BSP
					placed = true;
					break;
				}
			}

			if (!placed) {
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d);
				activeBins.add(newBin);
				initBinState(newBin);

				// For the new bin, try to fit
				Placement fit = findFit(box, newBin);
				if (fit != null) {
					placeBoxGPU(box, newBin, fit.spaceIndex);
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
			}
		}

		for (Bin bin : activeBins) {
			result.add(bin.boxes);
		}

		return result;
	}

	private void initBinState(Bin bin) {
		GPUBinState state = new GPUBinState(ocl, 3, 1);
		binStates.put(bin, state);
		fullRewrite(bin); // Write initial spaces
	}

	// Writes all spaces of the bin to the GPU buffer
	private void fullRewrite(Bin bin) {
		GPUBinState state = binStates.get(bin);
		List<Space> spaces = bin.freeSpaces;
		int numSpaces = spaces.size();

		// Resize if needed
		if (numSpaces > state.capacity) {
			state.allocateBuffers(Math.max(state.capacity * 2, numSpaces));
		}

		float[] spaceData = new float[numSpaces * 3];
		for (int i = 0; i < numSpaces; i++) {
			Space s = spaces.get(i);
			spaceData[i * 3 + 0] = s.w;
			spaceData[i * 3 + 1] = s.h;
			spaceData[i * 3 + 2] = s.d;
		}

		// Write to GPU
		if (numSpaces > 0) {
			clEnqueueWriteBuffer(ocl.clQueue, state.inputBuffer, CL_TRUE, 0,
					(long) Sizeof.cl_float * spaceData.length, Pointer.to(spaceData), 0, null, null);
		}
		state.count = numSpaces;
	}

	private void updateSpaceOnGPU(GPUBinState state, int index, Space space) {
		float[] data = new float[] { space.w, space.h, space.d };
		clEnqueueWriteBuffer(ocl.clQueue, state.inputBuffer, CL_TRUE,
				(long) Sizeof.cl_float * index * 3,
				(long) Sizeof.cl_float * 3,
				Pointer.to(data), 0, null, null);
	}

	// Implements BSP style placement (disjoint spaces, no partial overlaps)
	private void placeBoxGPU(Box box, Bin bin, int spaceIndex) {
		GPUBinState state = binStates.get(bin);
		Space space = bin.freeSpaces.get(spaceIndex);

		// Add box to bin
		Box placedBox = new Box(box.id,
				new Point3f(space.x, space.y, space.z),
				new Point3f(box.size.x, box.size.y, box.size.z));
		bin.boxes.add(placedBox);

		// Calculate new spaces (BSP)
		// Right: Remaining width
		Space right = new Space(space.x + box.size.x, space.y, space.z,
				space.w - box.size.x, space.h, space.d);

		// Top: Remaining height above box (width limited to box width)
		Space top = new Space(space.x, space.y + box.size.y, space.z,
				box.size.x, space.h - box.size.y, space.d);

		// Front: Remaining depth in front of box (width/height limited to box w/h)
		Space front = new Space(space.x, space.y, space.z + box.size.z,
				box.size.x, box.size.y, space.d - box.size.z);

		// Remove the 'used' space using swap-remove
		int lastIdx = bin.freeSpaces.size() - 1;
		if (spaceIndex != lastIdx) {
			Space lastSpace = bin.freeSpaces.get(lastIdx);
			bin.freeSpaces.set(spaceIndex, lastSpace);
			updateSpaceOnGPU(state, spaceIndex, lastSpace);
		}
		bin.freeSpaces.remove(lastIdx);
		state.count--;

		// Add new valid spaces
		addSpaceIfValid(bin, state, right);
		addSpaceIfValid(bin, state, top);
		addSpaceIfValid(bin, state, front);
	}

	private void addSpaceIfValid(Bin bin, GPUBinState state, Space space) {
		if (space.w > 0 && space.h > 0 && space.d > 0) {
			bin.freeSpaces.add(space);
			int index = bin.freeSpaces.size() - 1;

			// Resize check
			if (bin.freeSpaces.size() > state.capacity) {
				fullRewrite(bin);
			} else {
				// Append to GPU
				updateSpaceOnGPU(state, index, space);
				state.count++;
			}
		}
	}

	public void release() {
		for (GPUBinState state : binStates.values()) {
			state.release();
		}
		binStates.clear();

		clReleaseKernel(firstFitKernel);
		ocl.release();
	}

	private Placement findFit(Box box, Bin bin) {
		List<Space> spaces = bin.freeSpaces;
		if (spaces.isEmpty())
			return null;

		GPUBinState state = binStates.get(bin);
		int numSpaces = spaces.size();

		// Set args
		clSetKernelArg(firstFitKernel, 0, Sizeof.cl_float, Pointer.to(new float[] { box.size.x }));
		clSetKernelArg(firstFitKernel, 1, Sizeof.cl_float, Pointer.to(new float[] { box.size.y }));
		clSetKernelArg(firstFitKernel, 2, Sizeof.cl_float, Pointer.to(new float[] { box.size.z }));
		clSetKernelArg(firstFitKernel, 3, Sizeof.cl_mem, Pointer.to(state.inputBuffer));
		clSetKernelArg(firstFitKernel, 4, Sizeof.cl_mem, Pointer.to(state.outputBuffer));

		// Run
		long[] globalWorkSize = new long[] { numSpaces };
		clEnqueueNDRangeKernel(ocl.clQueue, firstFitKernel, 1, null, globalWorkSize, null, 0, null, null);

		// Read back results
		// We only need to read back 'numSpaces' results
		float[] results = new float[numSpaces];
		clEnqueueReadBuffer(ocl.clQueue, state.outputBuffer, CL_TRUE, 0,
				(long) Sizeof.cl_float * numSpaces, Pointer.to(results), 0, null, null);

		for (int i = 0; i < numSpaces; i++) {
			if (results[i] > 0) {
				Space fittedSpace = spaces.get(i);
				box.position.x = fittedSpace.x;
				box.position.y = fittedSpace.y;
				box.position.z = fittedSpace.z;

				return new Placement(box, i);
			}
		}

		return null;
	}

}
