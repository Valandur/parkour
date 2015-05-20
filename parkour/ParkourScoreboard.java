package parkour;

import org.bukkit.block.Sign;

public class ParkourScoreboard {
	private Sign nameSign;
	private Sign scoreSign;
	
	public Sign getNameSign() {
		return nameSign;
	}
	public Sign getScoreSign() {
		return scoreSign;
	}
	
	
	public ParkourScoreboard(Sign nameSign, Sign scoreSign) {
		this.nameSign = nameSign;
		this.scoreSign = scoreSign;
	}
}
