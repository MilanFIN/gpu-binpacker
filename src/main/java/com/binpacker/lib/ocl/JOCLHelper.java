package com.binpacker.lib.ocl;

import org.jocl.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jocl.CL.*;

public class JOCLHelper {

	static {
		// Enable JOCL exceptions for easier debugging
		CL.setExceptionsEnabled(true);
	}

	/**
	 * Represents an available OpenCL device.
	 */
	public static class OpenCLDevice {
		public final int platformIndex;
		public final int deviceIndex;
		public final String name;

		public OpenCLDevice(int platformIndex, int deviceIndex, String name) {
			this.platformIndex = platformIndex;
			this.deviceIndex = deviceIndex;
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	/**
	 * Returns a list of all available OpenCL devices.
	 */
	public static List<OpenCLDevice> getAvailableDevices() {
		List<OpenCLDevice> list = new ArrayList<>();

		// Obtain the number of platforms
		int[] numPlatformsArray = new int[1];
		try {
			clGetPlatformIDs(0, null, numPlatformsArray);
		} catch (CLException e) {
			// No platforms found or other error
			return list;
		}
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

		return list;
	}

	private static String getDeviceString(cl_device_id device, int paramName) {
		long[] size = new long[1];
		clGetDeviceInfo(device, paramName, 0, null, size);
		byte[] buffer = new byte[(int) size[0]];
		clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);
		return new String(buffer).trim();
	}

	// ========================================================================

	// ========================================================================
	// PUBLIC TEST FUNCTION
	// ========================================================================

	/**
	 * Prints all OpenCL platforms and devices.
	 */
	public static void testOpenCL() {
		int[] numPlatformsArray = new int[1];
		clGetPlatformIDs(0, null, numPlatformsArray);
		int numPlatforms = numPlatformsArray[0];
		System.out.println("Number of OpenCL platforms: " + numPlatforms);

		if (numPlatforms == 0) {
			System.out.println("No OpenCL platforms found!");
			return;
		}

		cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
		clGetPlatformIDs(numPlatforms, platforms, null);

		for (int i = 0; i < numPlatforms; i++) {
			System.out.println("Platform " + i + ":");
			printPlatformInfo(platforms[i], CL_PLATFORM_NAME);
			printPlatformInfo(platforms[i], CL_PLATFORM_VENDOR);
			printPlatformInfo(platforms[i], CL_PLATFORM_VERSION);

			int[] numDevicesArray = new int[1];
			int result = clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_ALL, 0, null, numDevicesArray);
			int numDevices = (result == CL_SUCCESS) ? numDevicesArray[0] : 0;

			if (numDevices == 0) {
				System.out.println("  No devices found on this platform.");
				continue;
			}

			cl_device_id[] devices = new cl_device_id[numDevices];
			clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_ALL, numDevices, devices, null);

			for (int d = 0; d < devices.length; d++) {
				System.out.print("  Device " + d + ": ");
				printDeviceInfo(devices[d], CL_DEVICE_NAME);
			}
		}
	}

	private static void printPlatformInfo(cl_platform_id platform, int paramName) {
		long[] size = new long[1];
		clGetPlatformInfo(platform, paramName, 0, null, size);

		byte[] buffer = new byte[(int) size[0]];
		clGetPlatformInfo(platform, paramName, buffer.length, Pointer.to(buffer), null);

		System.out.println("    " + new String(buffer).trim());
	}

	private static void printDeviceInfo(cl_device_id device, int paramName) {
		long[] size = new long[1];
		clGetDeviceInfo(device, paramName, 0, null, size);

		byte[] buffer = new byte[(int) size[0]];
		clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);

		System.out.println(new String(buffer).trim());
	}

	// ========================================================================
	// KERNEL LOADING
	// ========================================================================

	/**
	 * Loads a .cl kernel file from resources folder.
	 * Example: "vector_add.cl"
	 */
	private static String loadKernelSource(String resourcePath) {
		try (InputStream is = JOCLHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (is == null) {
				throw new RuntimeException("Kernel file not found: " + resourcePath);
			}
			return new String(is.readAllBytes());
		} catch (IOException e) {
			throw new RuntimeException("Error loading kernel source file", e);
		}
	}

	/**
	 * The source code of the OpenCL program to execute
	 */
	private static String programSource = "__kernel void " +
			"sampleKernel(__global const float *a," +
			"             __global const float *b," +
			"             __global float *c)" +
			"{" +
			"    int gid = get_global_id(0);" + // get_global_id(0)
			"    c[gid] = a[gid] * b[gid];" +
			"    printf(\"Work-item %d executing\\n\", gid);" +
			"}";

	/**
	 * Adapted from JOCL Sample.
	 */
	public static void runSample() {
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

		// The platform, device type and device number
		// that will be used
		final int platformIndex = 1;
		final long deviceType = CL_DEVICE_TYPE_ALL;
		final int deviceIndex = 0;

		// Enable exceptions and subsequently omit error checks in this sample
		CL.setExceptionsEnabled(true);

		// Obtain the number of platforms
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
				1, new String[] { programSource }, null, null);

		// Build the program
		clBuildProgram(program, 0, null, null, null, null);

		// Create the kernel
		cl_kernel kernel = clCreateKernel(program, "sampleKernel", null);

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

		// Release kernel, program, and memory objects
		// cleanup(context, commandQueue, program, kernel, srcMemA, srcMemB, dstMem);

		// Verify the result
		boolean passed = true;
		final float epsilon = 1e-7f;
		for (int i = 0; i < n; i++) {
			float x = dstArray[i];
			float y = srcArrayA[i] * srcArrayB[i];
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

	public static void runVectorAddition() {
		// OpenCL kernel source
		// String programSource = "__kernel void " +
		// "sampleKernel(__global const float *a," +
		// " __global const float *b," +
		// " __global float *c)" +
		// "{" +
		// " int gid = get_global_id(0);" +
		// " c[gid] = a[gid] + b[gid];" +
		// " printf(\"Work-item %d executing\\n\", gid);" +
		// "}";

		String programSource = "__kernel void " +
				"sampleKernel(__global const float *a," +
				"             __global const float *b," +
				"             __global float *c)" +
				"{" +
				"    uint gid = 5;" + // get_local_id(0)
				"    printf(\"gid=%u\\n\", gid);" + //
				"    c[gid] = a[gid] * b[gid];" +
				"}";

		// Enable exceptions
		CL.setExceptionsEnabled(true);

		// Get platform
		int numPlatformsArray[] = new int[1];
		clGetPlatformIDs(0, null, numPlatformsArray);
		cl_platform_id platforms[] = new cl_platform_id[numPlatformsArray[0]];
		clGetPlatformIDs(platforms.length, platforms, null);
		cl_platform_id platform = platforms[0];

		// Get device
		int numDevicesArray[] = new int[1];
		clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, null, numDevicesArray);
		cl_device_id devices[] = new cl_device_id[numDevicesArray[0]];
		clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, numDevicesArray[0], devices, null);
		cl_device_id device = devices[0];

		// Create context
		cl_context_properties contextProperties = new cl_context_properties();
		contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
		cl_context context = clCreateContext(
				contextProperties, 1, new cl_device_id[] { device }, null, null, null);

		// Create command queue
		cl_command_queue commandQueue = clCreateCommandQueue(context, device, 0, null);

		// Create input/output data
		int n = 10;
		float srcArrayA[] = new float[n];
		float srcArrayB[] = new float[n];
		float dstArray[] = new float[n];
		for (int i = 0; i < n; i++) {
			srcArrayA[i] = i;
			srcArrayB[i] = i;
		}

		// Create buffers
		cl_mem srcMemA = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, Pointer.to(srcArrayA), null);
		cl_mem srcMemB = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, Pointer.to(srcArrayB), null);
		cl_mem dstMem = clCreateBuffer(context,
				CL_MEM_READ_WRITE,
				Sizeof.cl_float * n, null, null);

		// Create and build program
		cl_program program = clCreateProgramWithSource(context,
				1, new String[] { programSource }, null, null);
		clBuildProgram(program, 0, null, null, null, null);

		// Create kernel
		cl_kernel kernel = clCreateKernel(program, "sampleKernel", null);

		// Set kernel arguments
		int a = 0;
		clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(srcMemA));
		clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(srcMemB));
		clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(dstMem));

		// Execute kernel
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				new long[] { n }, null, 0, null, null);

		// Read results
		clEnqueueReadBuffer(commandQueue, dstMem, CL_TRUE, 0,
				n * Sizeof.cl_float, Pointer.to(dstArray), 0, null, null);

		// Verify results
		boolean passed = true;
		final float epsilon = 1e-7f;
		for (int i = 0; i < n; i++) {
			float expected = srcArrayA[i] + srcArrayB[i];
			float actual = dstArray[i];
			if (Math.abs(expected - actual) > epsilon * Math.abs(expected)) {
				passed = false;
				System.out.println("Mismatch at " + i + ": expected " + expected + ", got " + actual);
			}
		}

		System.out.println("Test " + (passed ? "PASSED" : "FAILED"));
		System.out.println("Result: " + java.util.Arrays.toString(dstArray));

		// Cleanup
		clReleaseMemObject(srcMemA);
		clReleaseMemObject(srcMemB);
		clReleaseMemObject(dstMem);
		clReleaseKernel(kernel);
		clReleaseProgram(program);
		clReleaseCommandQueue(commandQueue);
		clReleaseContext(context);
	}

}
