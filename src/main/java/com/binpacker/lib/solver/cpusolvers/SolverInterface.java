package com.binpacker.lib.solver.cpusolvers;

import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.common.SolverProperties;

public interface SolverInterface {
	void init(SolverProperties properties);

	List<List<Box>> solve(List<Box> boxes);

	void release();
}
