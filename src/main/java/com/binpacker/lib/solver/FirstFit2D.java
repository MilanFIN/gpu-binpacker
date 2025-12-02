package com.binpacker.lib.solver;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;

public class FirstFit2D implements Solver {

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
				default:
					System.err.println("Invalid growAxis specified: " + growAxis);
					binTemplate.h = Integer.MAX_VALUE;
					break;
			}
		}
		activeBins.add(new Bin(0, binTemplate.w, binTemplate.h));

		for (int b = 0; b < boxes.size(); b++) {
			Box box = boxes.get(b);
			boolean placed = false;
			for (Bin bin : activeBins) {
				for (int i = 0; i < bin.freeSpaces.size(); i++) {
					Space space = bin.freeSpaces.get(i);
					Box fittedBox = findFit(box, space);
					if (fittedBox != null) {
						placeBox(fittedBox, bin, i);
						placed = true;
						break;
					}
				}
				if (placed)
					break;
			}

			if (!growingBin && !placed) {
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h);
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
		// For 2D, we check if the first two dimensions fit in space.w and space.h

		// 1. (x, y, z)
		if (box.size.x <= space.w && box.size.y <= space.h) {
			return box;
		}

		// 2. (x, z, y)
		if (box.size.x <= space.w && box.size.z <= space.h) {
			return new Box(box.id, box.position, new Point3f(box.size.x, box.size.z, box.size.y));
		}

		// 3. (y, x, z)
		if (box.size.y <= space.w && box.size.x <= space.h) {
			return new Box(box.id, box.position, new Point3f(box.size.y, box.size.x, box.size.z));
		}

		// 4. (y, z, x)
		if (box.size.y <= space.w && box.size.z <= space.h) {
			return new Box(box.id, box.position, new Point3f(box.size.y, box.size.z, box.size.x));
		}

		// 5. (z, x, y)
		if (box.size.z <= space.w && box.size.x <= space.h) {
			return new Box(box.id, box.position, new Point3f(box.size.z, box.size.x, box.size.y));
		}

		// 6. (z, y, x)
		if (box.size.z <= space.w && box.size.y <= space.h) {
			return new Box(box.id, box.position, new Point3f(box.size.z, box.size.y, box.size.x));
		}

		return null;
	}

	private void placeBox(Box box, Bin bin, int spaceIndex) {
		Space space = bin.freeSpaces.get(spaceIndex);

		Box placedBox = new Box(
				box.id,
				new Point3f(space.x, space.y, 0), // Z is 0 for 2D
				new Point3f(box.size.x, box.size.y, box.size.z));
		bin.boxes.add(placedBox);

		bin.freeSpaces.remove(spaceIndex);

		// Split the remaining space into two new ones
		Space top = new Space(space.x, space.y + box.size.y, space.w, space.h - box.size.y);
		Space rightSide = new Space(space.x + box.size.x, space.y, space.w - box.size.x, box.size.y);

		if (top.w > 0 && top.h > 0)
			bin.freeSpaces.add(top);
		if (rightSide.w > 0 && rightSide.h > 0)
			bin.freeSpaces.add(rightSide);
	}
}
