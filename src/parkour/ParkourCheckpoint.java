package parkour;

import org.bukkit.Location;

public class ParkourCheckpoint
{
	private int parkourId;
	private String name;
	private Location pos;
	
	public int getParkourId()
	{
		return parkourId;
	}
	public String getName()
	{
		return name;
	}
	public Location getPosition()
	{
		return pos;
	}
	
	
	public ParkourCheckpoint(int parkourId, String name, Location pos)
	{
		this.parkourId = parkourId;
		this.name = name;
		this.pos = pos;
	}
	
	@Override
	public String toString()
	{
		return name;
	}
}
