from spatium import Vec3i, Vec3

from .bases import SerializableNamedEnum


class Direction(SerializableNamedEnum):
    def __init__(self, _: str, index: int, ivec: Vec3i):
        self.index = index
        self.ivec = ivec
        self.vec = Vec3(ivec)

    DOWN = "down", 0, Vec3i(0, -1, 0)
    UP = "up", 1, Vec3i(0, 1, 0)
    NORTH = "north", 2, Vec3i(0, 0, -1)
    SOUTH = "south", 3, Vec3i(0, 0, 1)
    WEST = "west", 4, Vec3i(-1, 0, 0)
    EAST = "east", 5, Vec3i(1, 0, 0)
