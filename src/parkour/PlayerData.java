package parkour;

import org.bukkit.block.Sign;

public class PlayerData {
	private int currentPkId = -1;
	private long pkStartTime = -1;
	private long pkEndTime = -1;
	private boolean pkCompleted = false;
	private int pkEditId = -1;
	private int pkSbEditId = -1; 
	private Sign nameSign = null;
	
	public int getCurrentPkId() {
		return currentPkId;
	}
	public long getPkStartTime() {
		return pkStartTime;
	}
	public long getPkEndTime() {
		return pkEndTime;
	}
	public boolean getPkCompleted() {
		return pkCompleted;
	}
	public int getPkEditId() {
		return pkEditId;
	}
	public int getPkSbEditId() {
		return pkSbEditId;
	}
	public Sign getNameSign() {
		return nameSign;
	}
	
	public boolean isInParkour() {
		return currentPkId != -1;
	}
	public boolean hasStartedParkour() {
		return isInParkour() && pkStartTime != -1;
	}
	public boolean hasCompletedParkour() {
		return isInParkour() && pkCompleted;
	}
	
	
	public void enterParkour(int pkId) {
		if (!isInParkour()) {
			reset();
			this.currentPkId = pkId;
		}
	}
	public void startParkour(){
		if (isInParkour() && !hasStartedParkour()) {
			this.pkStartTime = System.nanoTime();
			this.pkCompleted = false;
		}
	}
	public void endParkour() {
		if (isInParkour() && hasStartedParkour()) {
			this.pkEndTime = System.nanoTime();
			this.pkCompleted = true;
		}
	}	
	public void reset() {
		currentPkId = -1;
		pkStartTime = -1;
		pkEndTime = -1;
		pkCompleted = false;
	}
	public float getParkourTime() {
		return ((pkEndTime - pkStartTime) / 100000000) / 10.0f;
	}
	
	public void editSb(int pkId, int sbId) {
		pkEditId = pkId;
		pkSbEditId = sbId;
		nameSign = null;
	}
	public void setNameSign(Sign nameSign) {
		this.nameSign = nameSign;
	}
	public void resetEdit() {
		pkEditId = -1;
		pkSbEditId = -1;
		nameSign = null;
	}
}
