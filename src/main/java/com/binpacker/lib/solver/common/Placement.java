package com.binpacker.lib.solver.common;

import com.binpacker.lib.common.Box;

public class Placement {
	public Box box;
	public int spaceIndex;

	public Placement(Box box, int spaceIndex) {
		this.box = box;
		this.spaceIndex = spaceIndex;
	}
}
