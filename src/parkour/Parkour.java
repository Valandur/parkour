package parkour;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Sign;

public class Parkour
{
	private int id;
	private String name;
	private Location startPos;
	private Location endPos;
	private Location minArea;
	private Location maxArea;
	private int maxHighscores;
	private List<ParkourHighscore> highscores = new ArrayList<ParkourHighscore>();
	private List<ParkourScoreboard> highscoreSigns = new ArrayList<ParkourScoreboard>();
	
	public int getId() {
		return id;
	}
	public String getName() {
		return name;
	}
	public Location getStartPosition() {
		return startPos;
	}
	public Location getEndPos() {
		return endPos;
	}
	public Location getMinArea() {
		return minArea;
	}
	public Location getMaxArea() {
		return maxArea;
	}
	public int getMaxHighscores() {
		return maxHighscores;
	}
	public List<ParkourHighscore> getHighscores() {
		return highscores;
	}
	public List<ParkourScoreboard> getHighscoreScoreboards() {
		return highscoreSigns;
	}
	
	
	public Parkour(int id, String name, int maxHighscores) {
		this.id = id;
		this.name = name;
		this.maxHighscores = maxHighscores;
	}
	
	public void setStartPos(Location pos) {
		this.startPos = pos;
	}
	public void setEndPos(Location pos) {
		this.endPos = pos;
	}
	public void setMinArea(Location pos) {
		this.minArea = pos;
	}
	public void setMaxArea(Location pos) {
		this.maxArea = pos;
	}
	
	public synchronized int checkNewTime(String playerName, float time) {
		// If the highscore list is full and the run was slower then the last one in the highscores, forget it
		if (highscores.size() == maxHighscores && time >= highscores.get(highscores.size() - 1).getTime())
			return -1;
		
		// Else we have a new highscore
		int newPos = -1;
		ParkourHighscore tempHs = null;
		for (int i = 0; i < highscores.size(); i++) {
			if (tempHs != null) {
				ParkourHighscore temp = highscores.get(i);
				highscores.set(i, tempHs);
				tempHs = temp;
			} else {
				if (highscores.get(i).getTime() < time) {
					continue;
				} else {
					tempHs = highscores.get(i);
					highscores.set(i, new ParkourHighscore(playerName, time));
					newPos = i;
				}
			}
		} 
		// If the highscore list isn't full yet re-add the last one / add the new one to the end
		if (highscores.size() < maxHighscores) {
			if (tempHs != null) {
				highscores.add(tempHs);
			} else {
				newPos = highscores.size();
				highscores.add(new ParkourHighscore(playerName, time));
			}
		}
		
		updateScoreboards();
		return newPos;
	}
	
	public void setScoreboard(int id, Sign nameSign, Sign scoreSign) {
		if (id < highscoreSigns.size()) {
			highscoreSigns.set(id, new ParkourScoreboard(nameSign, scoreSign));
		} else {
			for (int i = highscoreSigns.size(); i < id; i++) {
				highscoreSigns.add(null);
			}
			highscoreSigns.add(new ParkourScoreboard(nameSign, scoreSign));
		}
	}
	public void updateScoreboards() {
		for (int i = 0; i < highscoreSigns.size(); i++) {
			if (highscoreSigns.get(i) != null) {
				ParkourScoreboard sb = highscoreSigns.get(i);
				for (int j = 0; j < 4; j++) {
					if (i * 4 + j >= highscores.size()) {
						sb.getNameSign().setLine(j, "");
						sb.getScoreSign().setLine(j, "");
					} else {
						sb.getNameSign().setLine(j, highscores.get(i * 4 + j).getPlayerName());
						sb.getScoreSign().setLine(j, "" + highscores.get(i * 4 + j).getTime());
					}
				}
				sb.getNameSign().update(true);
				sb.getScoreSign().update(true);
			}
		}
	}
}
