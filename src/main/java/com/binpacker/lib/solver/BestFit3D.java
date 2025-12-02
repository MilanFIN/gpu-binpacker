package com.binpacker.lib.solver;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;

public class BestFit3D implements Solver {

	@Override
	public List<List<Box>> solve(List<Box> boxes, Bin binTemplate, boolean growingBin, String growAxis) {
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
					Box fittedBox = findFit(box, space);
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
				placeBox(bestBox, bestBin, bestSpaceIndex);
			} else {
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d);
				activeBins.add(newBin);
				Box fittedBox = findFit(box, newBin.freeSpaces.get(0));
				if (fittedBox != null) {
					placeBox(fittedBox, newBin, 0);
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

	private Box findFit(Box box, Space space) {
		// Check all 6 orientations (permutations of x, y, z)

		// 1. (x, y, z)
		if (box.size.x <= space.w && box.size.y <= space.h && box.size.z <= space.d) {
			return box;
		}

		// 2. (x, z, y)
		if (box.size.x <= space.w && box.size.z <= space.h && box.size.y <= space.d) {
			return new Box(box.id, box.position, new Point3f(box.size.x, box.size.z, box.size.y));
		}

		// 3. (y, x, z)
		if (box.size.y <= space.w && box.size.x <= space.h && box.size.z <= space.d) {
			return new Box(box.id, box.position, new Point3f(box.size.y, box.size.x, box.size.z));
		}

		// 4. (y, z, x)
		if (box.size.y <= space.w && box.size.z <= space.h && box.size.x <= space.d) {
			return new Box(box.id, box.position, new Point3f(box.size.y, box.size.z, box.size.x));
		}

		// 5. (z, x, y)
		if (box.size.z <= space.w && box.size.x <= space.h && box.size.y <= space.d) {
			return new Box(box.id, box.position, new Point3f(box.size.z, box.size.x, box.size.y));
		}

		// 6. (z, y, x)
		if (box.size.z <= space.w && box.size.y <= space.h && box.size.x <= space.d) {
			return new Box(box.id, box.position, new Point3f(box.size.z, box.size.y, box.size.x));
		}

		return null;
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

	private void placeBox(Box box, Bin bin, int spaceIndex) {
		Space space = bin.freeSpaces.get(spaceIndex);

		Box placedBox = new Box(
				box.id,
				new Point3f(space.x, space.y, space.z),
				new Point3f(box.size.x, box.size.y, box.size.z));
		bin.boxes.add(placedBox);

		bin.freeSpaces.remove(spaceIndex);

		// add empty spaces that are left after placing the box
		Space right = new Space(space.x + box.size.x, space.y, space.z,
				space.w - box.size.x, space.h, space.d);

		Space top = new Space(space.x, space.y + box.size.y, space.z,
				box.size.x, space.h - box.size.y, space.d);

		Space front = new Space(space.x, space.y, space.z + box.size.z,
				box.size.x, box.size.y, space.d - box.size.z);

		if (right.w > 0 && right.h > 0 && right.d > 0)
			bin.freeSpaces.add(right);
		if (top.w > 0 && top.h > 0 && top.d > 0)
			bin.freeSpaces.add(top);
		if (front.w > 0 && front.h > 0 && front.d > 0)
			bin.freeSpaces.add(front);
	}
}
