package com.binpacker.lib.solver;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;

public class MOAB implements Solver {

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
			boolean placed = false;
			for (Bin bin : activeBins) {
				float bestScore = Float.MAX_VALUE;
				Bin bestFitBin = null;
				int bestSpaceIndex = -1;
				Box bestFittedBox = null;

				for (int i = 0; i < bin.freeSpaces.size(); i++) {
					Space space = bin.freeSpaces.get(i);
					Box fittedBox = findFit(box, space);
					if (fittedBox != null) {
						float score = calculateScore(fittedBox, space);
						if (score < bestScore) {
							bestScore = score;
							bestFitBin = bin;
							bestSpaceIndex = i;
							bestFittedBox = fittedBox;
						}
					}
				}

				if (bestFittedBox != null) {
					Box placedBox = placeBox(bestFittedBox, bestFitBin, bestSpaceIndex);
					pruneCollidingSpaces(placedBox, bestFitBin);
					placed = true;

					bin.utilCounter++;
					if (bin.utilCounter > 10) {
						pruneWrappedSpacesBin(bin);
						bin.utilCounter = 0;
					}

					break; // Break from the activeBins loop, as we've placed the box
				}

			}

			if (!placed) {
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

	private Box placeBox(Box box, Bin bin, int spaceIndex) {
		Space space = bin.freeSpaces.get(spaceIndex);

		Box placedBox = new Box(
				box.id,
				new Point3f(space.x, space.y, space.z),
				new Point3f(box.size.x, box.size.y, box.size.z));
		bin.boxes.add(placedBox);

		bin.freeSpaces.remove(spaceIndex);

		Space right = new Space(space.x + box.size.x, space.y, space.z,
				space.w - box.size.x, space.h, space.d);

		Space top = new Space(space.x, space.y + box.size.y, space.z,
				space.w, space.h - box.size.y, space.d);

		Space front = new Space(space.x, space.y, space.z + box.size.z,
				space.w, space.h, space.d - box.size.z);

		if (right.w > 0 && right.h > 0 && right.d > 0)
			bin.freeSpaces.add(right);
		if (top.w > 0 && top.h > 0 && top.d > 0)
			bin.freeSpaces.add(top);
		if (front.w > 0 && front.h > 0 && front.d > 0)
			bin.freeSpaces.add(front);

		return placedBox;

	}

	private void pruneCollidingSpaces(Box box, Bin bin) {
		// can ignore 4 first ones, since those are created around the latest box
		// placement
		for (int i = bin.freeSpaces.size() - 1; i >= 0; i--) {
			Space space = bin.freeSpaces.get(i);
			if (box.collidesWith(space)) {
				bin.freeSpaces.remove(i);
				splitCollidingFreeSpace(box, space, bin);
			}
		}
	}

	private void splitCollidingFreeSpace(Box box, Space space, Bin bin) {
		// Create 4 new spaces around the box in the XY plane
		// Z and Depth are inherited from the original space

		// 1. Right space (from box right edge to space right edge)
		if (box.position.x + box.size.x < space.x + space.w) {
			Space right = new Space(
					box.position.x + box.size.x,
					space.y,
					space.z,
					(space.x + space.w) - (box.position.x + box.size.x),
					space.h,
					space.d);
			bin.freeSpaces.add(right);
		}

		// 2. Left space (from space left edge to box left edge)
		if (box.position.x > space.x) {
			Space left = new Space(
					space.x,
					space.y,
					space.z,
					box.position.x - space.x,
					space.h,
					space.d);
			bin.freeSpaces.add(left);
		}

		// 3. Top space (from box top edge to space top edge)
		if (box.position.y + box.size.y < space.y + space.h) {
			Space top = new Space(
					space.x,
					box.position.y + box.size.y,
					space.z,
					space.w,
					(space.y + space.h) - (box.position.y + box.size.y),
					space.d);
			bin.freeSpaces.add(top);
		}

		// 4. Bottom space (from space bottom edge to box bottom edge)
		if (box.position.y > space.y) {
			Space bottom = new Space(
					space.x,
					space.y,
					space.z,
					space.w,
					box.position.y - space.y,
					space.d);
			bin.freeSpaces.add(bottom);
		}

		// 5. Front space (from box front edge to space front edge)
		if (box.position.z + box.size.z < space.z + space.d) {
			Space front = new Space(
					space.x,
					space.y,
					box.position.z + box.size.z,
					space.w,
					space.h,
					(space.z + space.d) - (box.position.z + box.size.z));
			bin.freeSpaces.add(front);
		}

		// 6. Back space (from space back edge to box back edge)
		if (box.position.z > space.z) {
			Space back = new Space(
					space.x,
					space.y,
					space.z,
					space.w,
					space.h,
					box.position.z - space.z);
			bin.freeSpaces.add(back);
		}

	}

	void pruneWrappedSpacesBin(Bin bin) {
		for (int i = bin.freeSpaces.size() - 1; i >= 0; i--) {
			Space space1 = bin.freeSpaces.get(i);
			// Remove invalid spaces (zero or negative dimensions)
			if (space1.w <= 0 || space1.h <= 0 || space1.d <= 0) {
				bin.freeSpaces.remove(i);
				continue;
			}

			boolean isWrapped = false;
			for (int j = bin.freeSpaces.size() - 1; j >= 0; j--) {
				if (i == j) {
					continue; // Don't compare a space with itself
				}
				Space space2 = bin.freeSpaces.get(j);

				// Check if space1 is completely contained within space2
				if (space1.x >= space2.x &&
						space1.y >= space2.y &&
						space1.z >= space2.z &&
						(space1.x + space1.w) <= (space2.x + space2.w) &&
						(space1.y + space1.h) <= (space2.y + space2.h) &&
						(space1.z + space1.d) <= (space2.z + space2.d)) {
					isWrapped = true;
					break; // space1 is wrapped, no need to check further
				}
			}

			if (isWrapped) {
				bin.freeSpaces.remove(i);
			}
		}
	}

	void pruneWrappedSpaces(List<Bin> activeBins) {
		for (Bin bin : activeBins) {
			pruneWrappedSpacesBin(bin);
		}
	}

	private float calculateScore(Box box, Space space) {
		// Add a component for distance from origin (smaller x, y, z is better)
		// Assuming space.x, space.y, space.z are non-negative.
		float distanceScore = space.x + space.y + space.z;

		return distanceScore;

	}

}
