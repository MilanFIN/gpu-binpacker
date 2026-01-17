package com.binpacker.lib.common;

import java.util.ArrayList;
import java.util.List;

public class Bin {
	public List<Box> boxes = new ArrayList<>();
	public List<Space> freeSpaces = new ArrayList<>();
	public int index;
	public int utilCounter = 0;
	public float w;
	public float h;
	public float d;
	public float weight = 0;
	public float maxWeight = 0;

	public Bin(int index, float w, float h, float d) {
		this.index = index;
		this.w = w;
		this.h = h;
		this.d = d;
		freeSpaces.add(new Space(0, 0, 0, w, h, d));
	}

	public Bin(int index, float w, float h) {
		this(index, w, h, 0);
	}

	public Bin(int index, float w, float h, float d, float maxWeight) {
		this(index, w, h, d);
		this.maxWeight = maxWeight;
	}

	public Bin(int index, Box binTemplate) {
		this(index, binTemplate.size.x, binTemplate.size.y, binTemplate.size.z);
	}

	public Bin(Box binTemplate) {
		this(0, binTemplate);
	}

	public double getVolume() {
		return w * h * d;
	}
}
