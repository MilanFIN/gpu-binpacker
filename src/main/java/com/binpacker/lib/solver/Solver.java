package com.binpacker.lib.solver;

import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;

public interface Solver {
	List<List<Box>> solve(List<Box> boxes, Bin bin, boolean growingBin, String growAxis);
}
