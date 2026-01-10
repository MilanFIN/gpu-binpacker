package com.binpacker.lib.common;

public class Box {
	public int id = 0;
	public Point3f position;
	public Point3f size;

	public Box(int id, Point3f position, Point3f size) {
		this.id = id;
		this.position = position;
		this.size = size;
	}

	public Box(Point3f position, Point3f size) {
		this.position = position;
		this.size = size;
	}

	@Override
	public String toString() {
		return String.format("Box(pos=%s, size=%s)", position, size);
	}

	public double getVolume() {
		return size.x * size.y * size.z;
	}

	public boolean collidesWith(Box other) {
		return position.x < other.position.x + other.size.x &&
				position.y < other.position.y + other.size.y &&
				position.z < other.position.z + other.size.z &&
				position.x + size.x > other.position.x &&
				position.y + size.y > other.position.y &&
				position.z + size.z > other.position.z;
	}

	public boolean collidesWith(Space space) {
		return position.x < space.x + space.w &&
				position.y < space.y + space.h &&
				position.z < space.z + space.d &&
				position.x + size.x > space.x &&
				position.y + size.y > space.y &&
				position.z + size.z > space.z;
	}

	public double getLongestSide() {
		return Math.max(size.x, Math.max(size.y, size.z));
	}
}
