package com.binpacker.lib.common;

import java.util.ArrayList;
import java.util.List;

public class Bin {
	public List<Box> boxes = new ArrayList<>();
	public List<Space> freeSpaces = new ArrayList<>();
	public int index;
	public int utilCounter = 0;

	public Bin(int index, float w, float h, float d) {
		this.index = index;
		freeSpaces.add(new Space(0, 0, 0, w, h, d));
	}

	public Bin(int index, float w, float h) {
		this(index, w, h, 0);
	}

	public Bin(int index, Box binTemplate) {
		this(index, binTemplate.size.x, binTemplate.size.y, binTemplate.size.z);
	}

	public Bin(Box binTemplate) {
		this(0, binTemplate);
	}
}
