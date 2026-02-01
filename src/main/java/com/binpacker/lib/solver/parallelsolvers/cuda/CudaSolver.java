package com.binpacker.lib.solver.parallelsolvers.cuda;

import java.util.Collections;
import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.common.SolverProperties;
import com.binpacker.lib.solver.parallelsolvers.ParallelSolverInterface;

public class CudaSolver implements ParallelSolverInterface {

	@Override
	public void init(SolverProperties properties) {
		System.out.println("Initializing CUDA Solver (Dummy)");
	}

	@Override
	public List<Double> solve(List<Box> boxes, List<List<Integer>> orders) {
		System.out.println("Solving with CUDA Solver (Dummy) - returning empty results");
		return Collections.emptyList();
	}

	@Override
	public void release() {
		System.out.println("Releasing CUDA Solver (Dummy)");
	}
}
