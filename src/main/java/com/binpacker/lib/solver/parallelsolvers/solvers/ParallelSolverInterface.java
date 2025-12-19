package com.binpacker.lib.solver.parallelsolvers.solvers;

import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.common.SolverProperties;

public interface ParallelSolverInterface {

	void init(SolverProperties properties);

	List<Integer> solve(List<Box> boxes, List<List<Integer>> orders);

	void release();

}
