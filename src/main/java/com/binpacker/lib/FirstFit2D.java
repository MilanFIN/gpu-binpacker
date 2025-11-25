package com.binpacker.lib;

import java.util.ArrayList;
import java.util.List;

public class FirstFit2D implements Solver {

	private static class BinContext {
		List<BoxSpec> boxes = new ArrayList<>();
		List<Space> freeSpaces = new ArrayList<>();

		BinContext(BoxSpec binTemplate) {
			// Initial free space is the whole bin (Z is ignored/0)
			freeSpaces.add(new Space(0, 0, binTemplate.size.x, binTemplate.size.y));
		}
	}

	@Override
	public List<List<BoxSpec>> solve(List<BoxSpec> boxes, BoxSpec binTemplate) {
		List<BinContext> activeBins = new ArrayList<>();
		List<List<BoxSpec>> result = new ArrayList<>();

		// Start with one bin
		activeBins.add(new BinContext(binTemplate));

		for (BoxSpec box : boxes) {
			boolean placed = false;

			// Try to fit in any existing bin
			for (BinContext bin : activeBins) {
				for (int i = 0; i < bin.freeSpaces.size(); i++) {
					Space space = bin.freeSpaces.get(i);
					BoxSpec fittedBox = findFit(box, space);
					if (fittedBox != null) {
						placeBox(fittedBox, bin, i);
						placed = true;
						break;
					}
				}
				if (placed)
					break;
			}

			if (!placed) {
				// Create new bin
				BinContext newBin = new BinContext(binTemplate);
				activeBins.add(newBin);
				// Try to place in the new bin
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
		// Check 3 orientations:
		// 1. Original (x, y, z)
		if (box.size.x <= space.w && box.size.y <= space.h) {
			return box;
		}

		// 2. Rotate 1: (x, z, y) -> y is now z, z is now y
		// We are placing on x,z face effectively? No, we are just swapping dimensions.
		// The user said "3 orientations".
		// Let's try:
		// 1. x, y (z is depth)
		// 2. x, z (y is depth) -> new x=x, new y=z, new z=y
		// 3. y, z (x is depth) -> new x=y, new y=z, new z=x

		// Orientation 2: x, z
		if (box.size.x <= space.w && box.size.z <= space.h) {
			return new BoxSpec(box.position, new Point3f(box.size.x, box.size.z, box.size.y));
		}

		// Orientation 3: y, z
		if (box.size.y <= space.w && box.size.z <= space.h) {
			return new BoxSpec(box.position, new Point3f(box.size.y, box.size.z, box.size.x));
		}

		// Also need to check swapped W/H for each?
		// User said: "A box is symmetrical so it can only be placed in 3 orientations"
		// Usually 2D packing allows 90 degree rotation (swap W/H).
		// If I interpreted "3 orientations" as just the 3 faces, I might be missing the
		// 90 degree turn on the face.
		// But the user corrected me saying "There aren't six orientations... only 3".
		// 6 comes from 3 faces * 2 rotations per face.
		// So "only 3" implies NO rotation on the face, just picking a face.
		// OR it implies that (W, H) is same as (H, W) which is not true for packing.
		// I will stick to the 3 faces interpretation as per my plan which was approved.

		return null;
	}

	private void placeBox(BoxSpec box, BinContext bin, int spaceIndex) {
		Space space = bin.freeSpaces.get(spaceIndex);

		// Create placed box
		BoxSpec placedBox = new BoxSpec(
				new Point3f(space.x, space.y, 0), // Z is 0 for 2D
				new Point3f(box.size.x, box.size.y, box.size.z));
		bin.boxes.add(placedBox);

		// Remove the used space
		bin.freeSpaces.remove(spaceIndex);

		// Split the remaining space
		Space top = new Space(space.x, space.y + box.size.y, space.w, space.h - box.size.y);
		Space rightSide = new Space(space.x + box.size.x, space.y, space.w - box.size.x, box.size.y);

		if (top.w > 0 && top.h > 0)
			bin.freeSpaces.add(top);
		if (rightSide.w > 0 && rightSide.h > 0)
			bin.freeSpaces.add(rightSide);
	}
}
