package com.binpacker.lib.solver.parallelsolvers.solvers;

import java.util.ArrayList;
import java.util.List;

import org.jocl.*;
import static org.jocl.CL.*;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.ocl.KernelUtils;
import com.binpacker.lib.solver.common.SolverProperties;
import com.binpacker.lib.solver.common.ocl.OCLCommon;

public class FirstFitGPU implements ParallelSolverInterface {

	private OCLCommon ocl = new OCLCommon();
	private cl_kernel firstFitKernel;
	private String kernelSource;
	private Bin binTemplate;

	@Override
	public void init(SolverProperties properties) {
		this.binTemplate = properties.bin;
		this.kernelSource = KernelUtils.loadKernelSource("firstfit_complete.cl");

		// Initialize OpenCL
		ocl.init(kernelSource, properties.openCLDevice);
		firstFitKernel = clCreateKernel(ocl.clProgram, "guillotine_first_fit", null);
	}

	@Override
	public List<Integer> solve(List<Box> boxes, List<List<Integer>> orders) {
		int numBoxes = boxes.size();
		int numOrders = orders.size();

		if (numBoxes == 0 || numOrders == 0) {
			return new ArrayList<>();
		}

		// 1. Prepare data
		float[] boxData = new float[numBoxes * 3];
		for (int i = 0; i < numBoxes; i++) {
			Box b = boxes.get(i);
			boxData[i * 3 + 0] = b.size.x;
			boxData[i * 3 + 1] = b.size.y;
			boxData[i * 3 + 2] = b.size.z;
		}

		int[] orderData = new int[numOrders * numBoxes];
		for (int i = 0; i < numOrders; i++) {
			List<Integer> order = orders.get(i);
			for (int j = 0; j < numBoxes; j++) {
				orderData[i * numBoxes + j] = order.get(j);
			}
		}

		// 2. Allocate buffers

		cl_mem boxesMem = clCreateBuffer(ocl.clContext,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * boxData.length, Pointer.to(boxData), null);

		cl_mem ordersMem = clCreateBuffer(ocl.clContext,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * orderData.length, Pointer.to(orderData), null);

		cl_mem scoresMem = clCreateBuffer(ocl.clContext,
				CL_MEM_WRITE_ONLY,
				Sizeof.cl_float * numOrders, null, null);

		// 3. Set kernel args
		int a = 0;
		clSetKernelArg(firstFitKernel, a++, Sizeof.cl_mem, Pointer.to(boxesMem));
		clSetKernelArg(firstFitKernel, a++, Sizeof.cl_mem, Pointer.to(ordersMem));
		clSetKernelArg(firstFitKernel, a++, Sizeof.cl_mem, Pointer.to(scoresMem));
		clSetKernelArg(firstFitKernel, a++, Sizeof.cl_int, Pointer.to(new int[] { numBoxes }));
		clSetKernelArg(firstFitKernel, a++, Sizeof.cl_float, Pointer.to(new float[] { binTemplate.w }));
		clSetKernelArg(firstFitKernel, a++, Sizeof.cl_float, Pointer.to(new float[] { binTemplate.h }));
		clSetKernelArg(firstFitKernel, a++, Sizeof.cl_float, Pointer.to(new float[] { binTemplate.d }));

		// 4. Run kernel
		long[] globalWorkSize = new long[] { numOrders };
		clEnqueueNDRangeKernel(ocl.clQueue, firstFitKernel, 1, null, globalWorkSize,
				null, 0, null, null);

		// 5. Read results
		float[] scores = new float[numOrders];
		clEnqueueReadBuffer(ocl.clQueue, scoresMem, CL_TRUE, 0,
				Sizeof.cl_float * numOrders, Pointer.to(scores), 0, null, null);

		// 6. Cleanup memory
		clReleaseMemObject(boxesMem);
		clReleaseMemObject(ordersMem);
		clReleaseMemObject(scoresMem);

		// 7. Convert results
		List<Integer> resultList = new ArrayList<>(numOrders);
		for (float score : scores) {
			resultList.add((int) score);
		}

		return resultList;

		// List<Integer> result = new ArrayList<>(numOrders);
		// for (int i = 0; i < numOrders; i++)
		// result.add(0);
		// return result;
	}

	@Override
	public void release() {
		if (firstFitKernel != null) {
			clReleaseKernel(firstFitKernel);
		}
		if (ocl != null) {
			ocl.release();
		}
	}
}
