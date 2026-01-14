package com.binpacker.lib.solver.cpusolvers;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;
import com.binpacker.lib.solver.common.PlacementUtils;
import com.binpacker.lib.solver.common.SolverProperties;

public class BestFitEMS implements SolverInterface {

	private Bin binTemplate;
	private boolean growingBin;
	private String growAxis;
	private List<Integer> rotationAxes;
	private float weightLimit;

	@Override
	public void init(SolverProperties properties) {
		this.binTemplate = properties.bin;
		this.growingBin = properties.growingBin;
		this.growAxis = properties.growAxis;
		this.rotationAxes = properties.rotationAxes;
		this.weightLimit = properties.weight;
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
			boolean placed = false;
			for (Bin bin : activeBins) {
				// Skip bin if weight limit would be exceeded
				if (weightLimit > 0 && bin.weight + box.weight > weightLimit) {
					continue;
				}
				float bestScore = Float.MAX_VALUE;
				Bin bestFitBin = null;
				int bestSpaceIndex = -1;
				Box bestFittedBox = null;

				for (int i = 0; i < bin.freeSpaces.size(); i++) {
					Space space = bin.freeSpaces.get(i);
					Box fittedBox = PlacementUtils.findFit(box, space, rotationAxes);
					if (fittedBox != null) {
						float score = PlacementUtils.calculateScoreEMS(fittedBox, space);
						if (score < bestScore) {
							bestScore = score;
							bestFitBin = bin;
							bestSpaceIndex = i;
							bestFittedBox = fittedBox;
						}
					}
				}

				if (bestFittedBox != null) {
					Box placedBox = PlacementUtils.placeBoxEMS(bestFittedBox, bestFitBin, bestSpaceIndex);
					PlacementUtils.pruneCollidingSpacesEMS(placedBox, bestFitBin);
					placed = true;

					bin.utilCounter++;
					if (bin.utilCounter > 10) {
						PlacementUtils.pruneWrappedSpacesBinEMS(bin);
						bin.utilCounter = 0;
					}

					break; // Break from the activeBins loop, as we've placed the box
				}

			}

			if (!placed) {
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d);
				activeBins.add(newBin);
				Box fittedBox = PlacementUtils.findFit(box, newBin.freeSpaces.get(0), rotationAxes);
				if (fittedBox != null) {
					PlacementUtils.placeBoxEMS(fittedBox, newBin, 0);
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

}
