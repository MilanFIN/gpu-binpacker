package com.binpacker.lib.optimizer;

import java.util.List;

import com.binpacker.lib.common.Box;

class Solution {
	final List<Integer> order;
	final double score;
	final List<List<Box>> solved;

	Solution(List<Integer> order, double score, List<List<Box>> solved) {
		this.order = order;
		this.score = score;
		this.solved = solved;
	}
}