package com.binpacker.lib;

import java.util.ArrayList;
import java.util.List;

public class BestFit3D implements Solver {

	private static class BinContext {
		List<BoxSpec> boxes = new ArrayList<>();
		List<Space> freeSpaces = new ArrayList<>();
		int index;

		BinContext(int index, BoxSpec binTemplate) {
			this.index = index;
			// Initial free space is the whole bin
			freeSpaces.add(new Space(0, 0, 0, binTemplate.size.x, binTemplate.size.y, binTemplate.size.z));
		}
	}

	@Override
	public List<List<BoxSpec>> solve(List<BoxSpec> boxes, BoxSpec binTemplate) {
		List<BinContext> activeBins = new ArrayList<>();
		List<List<BoxSpec>> result = new ArrayList<>();

		// Start with one bin
		activeBins.add(new BinContext(0, binTemplate));

		for (BoxSpec box : boxes) {
			float bestScore = Float.MAX_VALUE;
			BinContext bestBin = null;
			int bestSpaceIndex = -1;
			BoxSpec bestBox = null;

			// Find best fit across all bins
			for (BinContext bin : activeBins) {
				for (int i = 0; i < bin.freeSpaces.size(); i++) {
					Space space = bin.freeSpaces.get(i);
					BoxSpec fittedBox = findFit(box, space);
					if (fittedBox != null) {
						float score = calculateScore(fittedBox, space);
						// We want the smallest score (least wasted space).
						// If scores are equal, we prefer the current 'bestBin' (which is earlier in the
						// list)
						// or if bestBin is null, we take this one.
						// Since we iterate bins in order, strict < ensures we keep the earlier bin on
						// ties.
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
				// Place in the best spot found
				placeBox(bestBox, bestBin, bestSpaceIndex);
			} else {
				// No fit found, create new bin
				BinContext newBin = new BinContext(activeBins.size(), binTemplate);
				activeBins.add(newBin);
				// Try to place in the new bin (should fit if box <= bin size)
				// We assume the new bin has 1 free space at index 0
				BoxSpec fittedBox = findFit(box, newBin.freeSpaces.get(0));
				if (fittedBox != null) {
					placeBox(fittedBox, newBin, 0);
				} else {
					System.err.println("Box too big for bin: " + box);
				}
			}
		}

		// Collect results
		for (BinContext bin : activeBins) {
			result.add(bin.boxes);
		}

		return result;
	}

	private BoxSpec findFit(BoxSpec box, Space space) {
		// 1. Original (x, y, z)
		if (box.size.x <= space.w && box.size.y <= space.h && box.size.z <= space.d) {
			return box;
		}

		// 2. Rotate 1: (x, z, y)
		if (box.size.x <= space.w && box.size.z <= space.h && box.size.y <= space.d) {
			return new BoxSpec(box.position, new Point3f(box.size.x, box.size.z, box.size.y));
		}

		// 3. Rotate 2: (y, z, x)
		if (box.size.y <= space.w && box.size.z <= space.h && box.size.x <= space.d) {
			return new BoxSpec(box.position, new Point3f(box.size.y, box.size.z, box.size.x));
		}

		return null;
	}

	private float calculateScore(BoxSpec box, Space space) {
		// Score = volume of space - volume of box (wasted space in that specific free
		// space)
		// Smaller is better.
		float spaceVol = space.w * space.h * space.d;
		float boxVol = box.size.x * box.size.y * box.size.z;
		return spaceVol - boxVol;
	}

	private void placeBox(BoxSpec box, BinContext bin, int spaceIndex) {
		Space space = bin.freeSpaces.get(spaceIndex);

		// Create placed box
		BoxSpec placedBox = new BoxSpec(
				new Point3f(space.x, space.y, space.z),
				new Point3f(box.size.x, box.size.y, box.size.z));
		bin.boxes.add(placedBox);

		// Remove the used space
		bin.freeSpaces.remove(spaceIndex);

		// Split the remaining space into 3 non-overlapping spaces
		// Same logic as FirstFit3D

		// Right space
		Space right = new Space(space.x + box.size.x, space.y, space.z,
				space.w - box.size.x, space.h, space.d);

		// Top space
		Space top = new Space(space.x, space.y + box.size.y, space.z,
				box.size.x, space.h - box.size.y, space.d);

		// Front space
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
