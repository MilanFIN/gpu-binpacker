package com.binpacker.lib.solver.common.ocl;

import org.jocl.*;
import static org.jocl.CL.*;
import com.binpacker.lib.ocl.OpenCLDevice;

public class OCLCommon {

	public cl_context clContext;
	public cl_command_queue clQueue;
	public cl_program clProgram;

	public void init(String kernelSource, OpenCLDevice preference) {
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
		cl_queue_properties queueProperties = new cl_queue_properties();
		clQueue = clCreateCommandQueueWithProperties(clContext, device, queueProperties, null);

		// 5. Create Program
		if (kernelSource != null) {
			clProgram = clCreateProgramWithSource(clContext, 1, new String[] { kernelSource }, null, null);
			clBuildProgram(clProgram, 0, null, null, null, null);
		}
	}

	public cl_mem createBuffer(long flags, long size) {
		return clCreateBuffer(clContext, flags, size, null, null);
	}

	public void release() {
		if (clProgram != null)
			clReleaseProgram(clProgram);
		if (clQueue != null)
			clReleaseCommandQueue(clQueue);
		if (clContext != null)
			clReleaseContext(clContext);
	}
}
