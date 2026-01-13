package com.binpacker.lib.solver.parallelsolvers;

import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.common.SolverProperties;

public interface ParallelSolverInterface {

	void init(SolverProperties properties);

	List<Double> solve(List<Box> boxes, List<List<Integer>> orders);

	void release();

}
