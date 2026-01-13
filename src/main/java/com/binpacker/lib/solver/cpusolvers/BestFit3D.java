package com.binpacker.lib.solver.cpusolvers;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;
import com.binpacker.lib.solver.common.PlacementUtils;
import com.binpacker.lib.solver.common.SolverProperties;

public class BestFit3D implements SolverInterface {

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
				case "z":
					binTemplate.d = Integer.MAX_VALUE;
					break;
				default:
					System.err.println("Invalid growAxis specified: " + growAxis);
					binTemplate.h = Integer.MAX_VALUE;
					break;
			}
		}
		activeBins.add(new Bin(0, binTemplate.w, binTemplate.h, binTemplate.d));

		for (Box box : boxes) {
			float bestScore = Float.MAX_VALUE;
			Bin bestBin = null;
			int bestSpaceIndex = -1;
			Box bestBox = null;

			for (Bin bin : activeBins) {
				for (int i = 0; i < bin.freeSpaces.size(); i++) {
					Space space = bin.freeSpaces.get(i);
					Box fittedBox = PlacementUtils.findFit(box, space, rotationAxes);
					if (fittedBox != null) {
						float score = calculateScore(fittedBox, space);
						if (score < bestScore) {
							bestScore = score;
							bestBin = bin;
							bestSpaceIndex = i;
							bestBox = fittedBox;
						}
					}
				}
			}

			if (bestBin != null) {
				PlacementUtils.placeBoxBSP(bestBox, bestBin, bestSpaceIndex);
			} else {
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d);
				activeBins.add(newBin);
				Box fittedBox = PlacementUtils.findFit(box, newBin.freeSpaces.get(0), rotationAxes);
				if (fittedBox != null) {
					PlacementUtils.placeBoxBSP(fittedBox, newBin, 0);
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
				case "z":
					float maxZ = 0;
					for (Box placedBox : activeBins.get(0).boxes) {
						maxZ = Math.max(maxZ, placedBox.position.z + placedBox.size.z);
					}
					activeBins.get(0).d = maxZ;
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

	private float calculateScore(Box box, Space space) {
		float spaceVol = space.w * space.h * space.d;
		float boxVol = box.size.x * box.size.y * box.size.z;
		float wastedSpaceScore = spaceVol - boxVol;

		// Add a component for distance from origin (smaller x, y, z is better)
		// Assuming space.x, space.y, space.z are non-negative.
		float distanceScore = space.x + space.y + space.z;

		return wastedSpaceScore + distanceScore;

	}

}
