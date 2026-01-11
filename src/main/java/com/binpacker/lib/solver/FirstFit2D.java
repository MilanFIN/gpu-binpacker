package com.binpacker.lib.solver;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;
import com.binpacker.lib.solver.common.PlacementUtils;
import com.binpacker.lib.solver.common.SolverProperties;

public class FirstFit2D implements SolverInterface {

	private Bin binTemplate;
	private boolean growingBin;
	private String growAxis;
	private List<Integer> rotationAxes;

	@Override
	public void init(SolverProperties properties) {
		this.binTemplate = properties.bin;
		this.growingBin = properties.growingBin;
		this.growAxis = properties.growAxis;
		this.rotationAxes = properties.rotationAxes;
	}

	@Override
	public List<List<Box>> solve(List<Box> boxes) {
		List<Bin> activeBins = new ArrayList<>();
		List<List<Box>> result = new ArrayList<>();

		if (growingBin) {
			switch (growAxis) {
				case "x":
					binTemplate.w = Integer.MAX_VALUE;
					break;
				case "y":
					binTemplate.h = Integer.MAX_VALUE;
					break;
				default:
					System.err.println("Invalid growAxis specified: " + growAxis);
					binTemplate.h = Integer.MAX_VALUE;
					break;
			}
		}
		activeBins.add(new Bin(0, binTemplate.w, binTemplate.h, binTemplate.d));

		for (int b = 0; b < boxes.size(); b++) {
			Box box = boxes.get(b);
			boolean placed = false;
			for (Bin bin : activeBins) {
				for (int i = 0; i < bin.freeSpaces.size(); i++) {
					Space space = bin.freeSpaces.get(i);
					Box fittedBox = PlacementUtils.findFit(box, space, rotationAxes);
					if (fittedBox != null) {
						PlacementUtils.placeBoxBSP2D(fittedBox, bin, i);
						placed = true;
						break;
					}
				}
				if (placed)
					break;
			}

			if (!growingBin && !placed) {
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d);
				activeBins.add(newBin);
				Box fittedBox = PlacementUtils.findFit(box, newBin.freeSpaces.get(0), rotationAxes);
				if (fittedBox != null) {
					PlacementUtils.placeBoxBSP2D(fittedBox, newBin, 0);
				} else {
					System.err.println("Box too big for bin: " + box);
				}
			}
		}

		if (growingBin) {
			switch (growAxis) {
				case "x":
					float maxX = 0;
					for (Box placedBox : activeBins.get(0).boxes) {
						maxX = Math.max(maxX, placedBox.position.x + placedBox.size.x);
					}
					activeBins.get(0).w = maxX;
					break;
				case "y":
					float maxY = 0;
					for (Box placedBox : activeBins.get(0).boxes) {
						maxY = Math.max(maxY, placedBox.position.y + placedBox.size.y);
					}
					activeBins.get(0).h = maxY;
					break;
				default:
					System.err.println("Invalid growAxis specified for final bin sizing: " + growAxis);
					break;
			}
		}

		for (Bin bin : activeBins) {
			result.add(bin.boxes);
		}

		return result;
	}

	public void release() {
		// not used by this
	}

}
