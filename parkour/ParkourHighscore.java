package parkour;

public class ParkourHighscore {
	private String playerName;
	private float time;
	
	public String getPlayerName() {
		return playerName;
	}
	public float getTime() {
		return time;
	}
	
	
	public ParkourHighscore(String playerName, float time) {
		this.playerName = playerName;
		this.time = time;
	}
}
