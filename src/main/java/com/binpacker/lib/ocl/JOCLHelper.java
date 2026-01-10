package com.binpacker.lib.ocl;

import org.jocl.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.binpacker.lib.ocl.OpenCLDevice;

import static org.jocl.CL.*;

public class JOCLHelper {

	private static boolean isOpenCLAvailable = true;

	static {
		try {
			// Enable JOCL exceptions for easier debugging
			CL.setExceptionsEnabled(true);
		} catch (Throwable t) {
			isOpenCLAvailable = false;
			System.err.println("OpenCL is not available: " + t.getMessage());
		}
	}

	/**
	 * Returns a list of all available OpenCL devices.
	 */
	public static List<OpenCLDevice> getAvailableDevices() {
		List<OpenCLDevice> list = new ArrayList<>();

		if (!isOpenCLAvailable) {
			return list;
		}

		try {
			// Obtain the number of platforms
			int[] numPlatformsArray = new int[1];
			clGetPlatformIDs(0, null, numPlatformsArray);
			int numPlatforms = numPlatformsArray[0];

			if (numPlatforms == 0) {
				return list;
			}

			// Obtain platform IDs
			cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
			clGetPlatformIDs(platforms.length, platforms, null);

			for (int i = 0; i < numPlatforms; i++) {
				int[] numDevicesArray = new int[1];
				try {
					clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_ALL, 0, null, numDevicesArray);
				} catch (CLException e) {
					if (e.getStatus() == CL_DEVICE_NOT_FOUND) {
						continue;
					}
					throw e;
				}
				int numDevices = numDevicesArray[0];

				if (numDevices == 0) {
					continue;
				}

				cl_device_id[] devices = new cl_device_id[numDevices];
				clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_ALL, numDevices, devices, null);

				for (int d = 0; d < numDevices; d++) {
					String name = getDeviceString(devices[d], CL_DEVICE_NAME);
					list.add(new OpenCLDevice(i, d, name));
				}
			}
		} catch (Throwable t) {
			System.err.println("Failed to get OpenCL devices: " + t.getMessage());
			isOpenCLAvailable = false;
			return new ArrayList<>(); // Return empty list on failure
		}

		return list;
	}

	public static boolean testOpenCLDevice(OpenCLDevice device) {
		if (!isOpenCLAvailable) {
			return false;
		}

		try {
			// Re-obtain the cl_device_id for the given OpenCLDevice
			cl_platform_id[] platforms = new cl_platform_id[1];
			clGetPlatformIDs(1, platforms, null); // Get at least one platform to start
			clGetPlatformIDs(0, null, new int[1]); // Get total number of platforms
			int[] numPlatformsArray = new int[1];
			clGetPlatformIDs(0, null, numPlatformsArray);
			cl_platform_id[] allPlatforms = new cl_platform_id[numPlatformsArray[0]];
			clGetPlatformIDs(numPlatformsArray[0], allPlatforms, null);

			cl_platform_id platform = allPlatforms[device.platformIndex];

			int[] numDevicesArray = new int[1];
			clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, null, numDevicesArray);
			cl_device_id[] allDevices = new cl_device_id[numDevicesArray[0]];
			clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, numDevicesArray[0], allDevices, null);

			cl_device_id clDevice = allDevices[device.deviceIndex];

			// Create a context for the device
			cl_context_properties contextProperties = new cl_context_properties();
			contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

			cl_context context = clCreateContext(contextProperties, 1, new cl_device_id[] { clDevice }, null, null,
					null);

			// Create a command queue
			cl_command_queue commandQueue = clCreateCommandQueue(context, clDevice, 0, null);

			// Simple kernel source
			String programSource = "__kernel void simple_add(__global const float* a, __global const float* b, __global float* c, int numElements) {\n"
					+
					"    int gid = get_global_id(0);\n" +
					"    if (gid < numElements) {\n" +
					"        c[gid] = a[gid] + b[gid];\n" +
					"    }\n" +
					"}\n";

			// Create the program
			cl_program program = clCreateProgramWithSource(context, 1, new String[] { programSource }, null, null);

			// Build the program
			clBuildProgram(program, 0, null, null, null, null);

			// Create the kernel
			cl_kernel kernel = clCreateKernel(program, "simple_add", null);

			// Prepare input data
			int numElements = 5;
			float[] hostInputA = new float[numElements];
			float[] hostInputB = new float[numElements];
			float[] hostOutputC = new float[numElements];

			for (int i = 0; i < numElements; i++) {
				hostInputA[i] = i;
				hostInputB[i] = i * 2;
			}

			// Create memory buffers
			cl_mem memA = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
					(long) Float.BYTES * numElements, Pointer.to(hostInputA), null);
			cl_mem memB = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
					(long) Float.BYTES * numElements, Pointer.to(hostInputB), null);
			cl_mem memC = clCreateBuffer(context, CL_MEM_WRITE_ONLY,
					(long) Float.BYTES * numElements, null, null);

			// Set kernel arguments
			clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(memA));
			clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(memB));
			clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(memC));
			clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[] { numElements }));

			// Execute the kernel
			long[] globalWorkSize = new long[] { numElements };
			clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, globalWorkSize, null, 0, null, null);

			// Read the output
			clEnqueueReadBuffer(commandQueue, memC, CL_TRUE, 0,
					(long) Float.BYTES * numElements, Pointer.to(hostOutputC), 0, null, null);

			// Release resources
			clReleaseMemObject(memA);
			clReleaseMemObject(memB);
			clReleaseMemObject(memC);
			clReleaseKernel(kernel);
			clReleaseProgram(program);
			clReleaseCommandQueue(commandQueue);
			clReleaseContext(context);

			// Optional: Verify results
			for (int i = 0; i < numElements; i++) {
				if (hostOutputC[i] != (hostInputA[i] + hostInputB[i])) {
					// Verification failed, though we only care if it runs without exception
					return false;
				}
			}

		} catch (CLException e) {
			System.err.println("OpenCL device test failed for device " + device.name + ": " + e.getMessage());
			return false;
		} catch (Exception e) {
			System.err.println("An unexpected error occurred during OpenCL device test for device " + device.name + ": "
					+ e.getMessage());
			return false;
		}

		return true;
	}

	private static String getDeviceString(cl_device_id device, int paramName) {
		long[] size = new long[1];
		clGetDeviceInfo(device, paramName, 0, null, size);
		byte[] buffer = new byte[(int) size[0]];
		clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);
		return new String(buffer).trim();
	}

	/**
	 * Adapted from JOCL Sample.
	 */
	public static void runSample(int platformIndex, int deviceIndex) {
		if (!isOpenCLAvailable) {
			System.err.println("OpenCL is not available. Skipping sample.");
			return;
		}

		final long deviceType = CL_DEVICE_TYPE_DEFAULT;

		// Create input- and output data
		int n = 10;
		float srcArrayA[] = new float[n];
		float srcArrayB[] = new float[n];
		float dstArray[] = new float[n];
		for (int i = 0; i < n; i++) {
			srcArrayA[i] = i + 1;
			srcArrayB[i] = i + 1;
		}
		Pointer srcA = Pointer.to(srcArrayA);
		Pointer srcB = Pointer.to(srcArrayB);
		Pointer dst = Pointer.to(dstArray);

		CL.setExceptionsEnabled(true);

		int numPlatformsArray[] = new int[1];
		clGetPlatformIDs(0, null, numPlatformsArray);
		int numPlatforms = numPlatformsArray[0];

		// Obtain a platform ID
		cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
		clGetPlatformIDs(platforms.length, platforms, null);
		cl_platform_id platform = platforms[platformIndex];

		// Initialize the context properties
		cl_context_properties contextProperties = new cl_context_properties();
		contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

		// Obtain the number of devices for the platform
		int numDevicesArray[] = new int[1];
		clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
		int numDevices = numDevicesArray[0];

		// Obtain a device ID
		cl_device_id devices[] = new cl_device_id[numDevices];
		clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
		cl_device_id device = devices[deviceIndex];

		// Create a context for the selected device
		cl_context context = clCreateContext(
				contextProperties, 1, new cl_device_id[] { device },
				null, null, null);

		// Create a command-queue for the selected device
		cl_queue_properties properties = new cl_queue_properties();
		cl_command_queue commandQueue = clCreateCommandQueueWithProperties(
				context, device, properties, null);

		// Allocate the memory objects for the input- and output data
		cl_mem srcMemA = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, srcA, null);
		cl_mem srcMemB = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, srcB, null);
		cl_mem dstMem = clCreateBuffer(context,
				CL_MEM_READ_WRITE,
				Sizeof.cl_float * n, null, null);

		// Create the program from the source code
		cl_program program = clCreateProgramWithSource(context,
				1, new String[] { KernelUtils.loadKernelSource("vector_add.cl") }, null, null);

		// Build the program
		clBuildProgram(program, 0, null, null, null, null);

		// Create the kernel
		cl_kernel kernel = clCreateKernel(program, "vector_add", null);

		// Set the arguments for the kernel
		int a = 0;
		clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(srcMemA));
		clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(srcMemB));
		clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(dstMem));

		// Set the work-item dimensions
		long global_work_size[] = new long[] { n };
		long[] local_work_size = new long[] { 1 }; // All items in one work-group

		// Execute the kernel
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				global_work_size, local_work_size, 0, null, null);

		// Read the output data
		clEnqueueReadBuffer(commandQueue, dstMem, CL_TRUE, 0,
				n * Sizeof.cl_float, dst, 0, null, null);

		// Cleanup
		clReleaseMemObject(srcMemA);
		clReleaseMemObject(srcMemB);
		clReleaseMemObject(dstMem);
		clReleaseKernel(kernel);
		clReleaseProgram(program);
		clReleaseCommandQueue(commandQueue);
		clReleaseContext(context);

		// Verify the result
		boolean passed = true;
		final float epsilon = 1e-7f;
		for (int i = 0; i < n; i++) {
			float x = dstArray[i];
			float y = srcArrayA[i] + srcArrayB[i];
			boolean epsilonEqual = Math.abs(x - y) <= epsilon * Math.abs(x);
			if (!epsilonEqual) {
				passed = false;
				break;
			}
		}
		System.out.println("Test " + (passed ? "PASSED" : "FAILED"));
		if (n <= 10) {
			System.out.println("Result: " + Arrays.toString(dstArray));
		}
	}

}
