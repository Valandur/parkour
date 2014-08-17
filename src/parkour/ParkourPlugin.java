package parkour;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class ParkourPlugin extends JavaPlugin implements Listener {
	private Logger logger = Logger.getLogger("Minecraft");
	private static String prefix = "[Parkour] ";
	private Connection conn = null;
	
	// Database
	private String db_server = "";
	private String db_port = "";
	private String db_name = "";
	private String db_un = "";
	private String db_pw = "";
	
	// Parkour
	private Map<Integer, Parkour> parkours = new HashMap<Integer, Parkour>();
	private Map<UUID, PlayerData> playerData = new HashMap<UUID, PlayerData>();
	private BukkitTask taskParkour;
	
	
	@Override
	public void onEnable() {
		logger.info(prefix + "Registering events...");
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(this, this);
		
		logger.info(prefix + "Reading config...");
		getConfig().addDefault("db_server", "localhost");
		getConfig().addDefault("db_port", 3306);
		getConfig().addDefault("db_name", "minecraft");
		getConfig().addDefault("db_username", "root");
		getConfig().addDefault("db_password", "");
		getConfig().options().copyDefaults(true);
		saveConfig();
		
		db_server = getConfig().getString("db_server");
		db_port = getConfig().getString("db_port");
		db_name = getConfig().getString("db_name");
		db_un = getConfig().getString("db_username");
		db_pw = getConfig().getString("db_password");
		
		logger.info(prefix + "Connecting to database...");
		connectToDB();
		
		try {
			Statement stmt = conn.createStatement();
			Statement stmt2 = conn.createStatement();
	        ResultSet rs = stmt.executeQuery("SELECT * FROM parkours");
	        while (rs.next()) {
	        	try {
		        	Parkour pk = new Parkour(rs.getInt("id"), rs.getString("name"), rs.getInt("max_highscores"));
		        	if (rs.getString("start_world") != null)
		        		pk.setStartPos(new Location(Bukkit.getWorld(rs.getString("start_world")), rs.getInt("start_pos_x"), rs.getInt("start_pos_y"), rs.getInt("start_pos_z")));
		        	if (rs.getString("end_world") != null)
		        		pk.setEndPos(new Location(Bukkit.getWorld(rs.getString("end_world")), rs.getInt("end_pos_x"), rs.getInt("end_pos_y"), rs.getInt("end_pos_z")));
		        	parkours.put(pk.getId(), pk);
		        	
		        	ResultSet rs2 = stmt2.executeQuery("SELECT * FROM highscores WHERE parkour_id = " + pk.getId() + " ORDER BY position;");
		        	while (rs2.next()) {
		        		pk.getHighscores().add(new ParkourHighscore(rs2.getString("player_name"), rs2.getFloat("time")));
		        	}
		        	rs2.close();
		        	
		        	rs2 = stmt2.executeQuery("SELECT * FROM scoreboards WHERE parkour_id = " + pk.getId() + " ORDER BY scoreboard_id;");
		        	while (rs2.next()) {
		        		pk.setScoreboard(rs2.getInt("scoreboard_id"), 
		        				(Sign)Bukkit.getWorld(rs2.getString("sign_name_world")).getBlockAt(rs2.getInt("sign_name_x"), rs2.getInt("sign_name_y"), rs2.getInt("sign_name_z")).getState(),  
		        				(Sign)Bukkit.getWorld(rs2.getString("sign_score_world")).getBlockAt(rs2.getInt("sign_score_x"), rs2.getInt("sign_score_y"), rs2.getInt("sign_score_z")).getState());
		        	}
		        	rs2.close();
		        	pk.updateScoreboards();
	        	} catch (Exception ex) {
	        		ex.printStackTrace();
	        	}
	        }
	        
	        rs.close();
	        stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		logger.info(prefix + "Handling logged in players...");
		for (Player p : Bukkit.getOnlinePlayers()) {
			PlayerData pd = new PlayerData();
			playerData.put(p.getUniqueId(), pd);
		}
		
		logger.info(prefix + "Starting threads...");
		taskParkour = getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() { public void run() { onPkTick(); } }, 20, 20);
	}
	@Override
	public void onDisable() {
		if (taskParkour != null)
			taskParkour.cancel();
	}
	
	private void connectToDB() {
		try {
			conn = DriverManager.getConnection("jdbc:mysql://" + db_server + ":" + db_port + "/" + db_name + "?user=" + db_un + "&password=" + db_pw + "&connect-timeout=0");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	
	// Command
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(prefix + "The Parkour commands can only be used by players");
			return false;
		}
		
		Player p = (Player)sender;
		PlayerData pd = playerData.get(p.getUniqueId());
		
		if (commandLabel.equalsIgnoreCase("pk")) {
			if (args.length <= 0) {
				p.sendMessage(prefix + ChatColor.RED + "Command syntax: " + ChatColor.GOLD + "/pk [cmd] [name] [args]");
				return false;
			}
			
			if (args[0].equalsIgnoreCase("add")) {
				// Composite name
				String name = args[1];
				for (int i = 2; i < args.length; i++)
					name += " " + args[i];
				
				// Create parkour
				Statement stmt;
				try {
					stmt = conn.createStatement();
					stmt.executeUpdate("INSERT INTO parkours (name) VALUES ('" + name + "');");
					ResultSet rs = stmt.executeQuery("SELECT * FROM parkours WHERE name = '" + name + "';");
					
					if (rs.next()) {
						Parkour pk = new Parkour(rs.getInt("id"), name, rs.getInt("max_highscores"));
						p.sendMessage(prefix + "Added parkour " + pk.getName() + " (" + pk.getId() + ")");
					}
					
					rs.close();
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} else if (args[0].equalsIgnoreCase("cancel")) {
				pd.reset();
				p.sendMessage(prefix + ChatColor.AQUA + "You have canceled your parkour run");
			} else if (args[0].equalsIgnoreCase("set")) {
				if (args.length <= 2) {
					p.sendMessage(prefix + ChatColor.RED + "Command syntax: " + ChatColor.GOLD + "/pk set [name] [cmd]");
					return false;
				}
				
				Parkour pk = null;
				for (Parkour park : parkours.values()) {
					if (park.getName().replace(" ", "").equalsIgnoreCase(args[1])) {
						pk = park;
						break;
					}
				}
				if (pk == null) {
					p.sendMessage(prefix + ChatColor.RED + "Invalid parkour name " + args[1]);
					return false;
				}
				
				if (args[2].equalsIgnoreCase("start")) {
					pk.setStartPos(p.getLocation());
					Statement stmt;
					try {
						stmt = conn.createStatement();
						stmt.executeUpdate("UPDATE parkours SET start_world = '" + p.getLocation().getWorld().getName() 
								+ "', start_pos_x = " + p.getLocation().getBlockX() 
								+ ", start_pos_y = " + p.getLocation().getBlockY() 
								+ ", start_pos_z = " + p.getLocation().getBlockZ()
								+ " WHERE id = " + pk.getId());
						stmt.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
					p.sendMessage(prefix + pk.getName() + " start position set");
				} else if (args[2].equalsIgnoreCase("end")) {
					pk.setEndPos(p.getLocation());
					Statement stmt;
					try {
						stmt = conn.createStatement();
						stmt.executeUpdate("UPDATE parkours SET end_world = '" + p.getLocation().getWorld().getName() 
								+ "', end_pos_x = " + p.getLocation().getBlockX() 
								+ ", end_pos_y = " + p.getLocation().getBlockY() 
								+ ", end_pos_z = " + p.getLocation().getBlockZ()
								+ " WHERE id = " + pk.getId());
						stmt.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
					p.sendMessage(prefix + pk.getName() + " end position set");
				} else if (args[2].equalsIgnoreCase("sb")) {
					int sb = Integer.parseInt(args[3]);
					pd.editSb(pk.getId(), sb);
					p.sendMessage(prefix + ChatColor.AQUA + "Please select the player names sign for scoreboard #" + sb + " for " + ChatColor.GOLD + pk.getName());
				} else if (args[2].equalsIgnoreCase("min")) {
					pk.setMinArea(p.getLocation());
					p.sendMessage(prefix + pk.getName() + " min position set");
				} else if (args[2].equalsIgnoreCase("max")) {
					pk.setMaxArea(p.getLocation());
					p.sendMessage(prefix + pk.getName() + " max position set");
				} else
					p.sendMessage(prefix + ChatColor.RED + "Invalid parkour set command");
			}
		}
		
		return true;
	}
	
	// Player
	@EventHandler (priority = EventPriority.NORMAL)
	public void onPlayerJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		
		if (playerData.containsKey(p.getUniqueId()))
			playerData.remove(p.getUniqueId());
		
		playerData.put(p.getUniqueId(), new PlayerData());
	}
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent e) {
		if (e.hasBlock() && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
			if (e.getClickedBlock().getType() == Material.SIGN_POST || e.getClickedBlock().getType() == Material.WALL_SIGN) {
				Player p = e.getPlayer();
				PlayerData pd = playerData.get(p.getUniqueId());
				if (pd.getPkSbEditId() != -1) {
					Parkour pk = parkours.get(pd.getPkEditId());
					Sign s = (Sign)e.getClickedBlock().getState();	
					if (pd.getNameSign() == null) {
						pd.setNameSign(s);
						p.sendMessage(prefix + ChatColor.AQUA + "Now please right click the corresponding scores sign");
					} else {
						pk.setScoreboard(pd.getPkSbEditId(), pd.getNameSign(), s);
						p.sendMessage(prefix + ChatColor.AQUA + "You have set the scoreboard for " + ChatColor.GOLD + pk.getName());
						pk.updateScoreboards();
						pd.resetEdit();
						
						try {
							Statement stmt = conn.createStatement();
							for (int i = 0; i < pk.getHighscoreScoreboards().size(); i++) {
								ParkourScoreboard sb = pk.getHighscoreScoreboards().get(i);
								stmt.executeUpdate("INSERT INTO scoreboards (parkour_id, scoreboard_id, sign_name_world, sign_name_x, sign_name_y, sign_name_z, "
										+ "sign_score_world, sign_score_x, sign_score_y, sign_score_z) VALUES (" + pk.getId() + "," + i + ",'" 
										+ sb.getNameSign().getWorld().getName() + "'," + sb.getNameSign().getX() + "," + sb.getNameSign().getY() + "," + sb.getNameSign().getZ() + ",'" 
										+ sb.getScoreSign().getWorld().getName() + "'," + sb.getScoreSign().getX() + "," + sb.getScoreSign().getY() + "," + sb.getScoreSign().getZ() 
										+ ") ON DUPLICATE KEY UPDATE sign_name_world='" + sb.getNameSign().getWorld().getName() + "',sign_name_x=" + sb.getNameSign().getX() 
										+ ",sign_name_y=" + sb.getNameSign().getY() + ",sign_name_z=" + sb.getNameSign().getZ() + ",sign_score_world='" + sb.getScoreSign().getWorld().getName() 
										+ "',sign_score_x=" + sb.getScoreSign().getX() + ",sign_score_y=" + sb.getScoreSign().getY() + ",sign_score_z=" + sb.getScoreSign().getZ());
							}
							stmt.close();
						} catch (SQLException e1) {
							e1.printStackTrace();
						}
					}
				}
			}
		}
	}
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent e) {
		Player p = e.getPlayer();
		PlayerData pd = playerData.get(p.getUniqueId());
		
		if (pd.isInParkour()) {
			Parkour pk = parkours.get(pd.getCurrentPkId());
			
			if (!pd.hasStartedParkour()) {
				if (!p.getLocation().getWorld().getUID().equals(pk.getStartPosition().getWorld().getUID()) || 
						p.getLocation().getBlockX() != pk.getStartPosition().getBlockX() || 
						p.getLocation().getBlockY() != pk.getStartPosition().getBlockY() || 
						p.getLocation().getBlockZ() != pk.getStartPosition().getBlockZ()) {
					pd.startParkour();
					p.sendMessage(prefix + ChatColor.AQUA + "You have started " + ChatColor.GOLD + pk.getName());
				}
			} else if (!pd.hasCompletedParkour()) {
				if (pk.getEndPos() == null)
					return;
				
				if (p.isFlying()) {
					pd.reset();
					p.sendMessage(prefix + ChatColor.AQUA + "You tried to fly in " + pk.getName() + ", run canceled");
					return;
				}
				
				if (p.getLocation().getWorld().getUID().equals(pk.getEndPos().getWorld().getUID()) && 
						p.getLocation().getBlockX() == pk.getEndPos().getBlockX() && 
						p.getLocation().getBlockY() == pk.getEndPos().getBlockY() && 
						p.getLocation().getBlockZ() == pk.getEndPos().getBlockZ()) {
					pd.endParkour();
					p.sendMessage(prefix + ChatColor.AQUA + "You completed " + ChatColor.GOLD + pk.getName() + ChatColor.AQUA + " in " + ChatColor.GREEN + pd.getParkourTime() + " secs.");
				}
			}
		}
	}
	
	// Other
	public void onPkTick() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			PlayerData pd = playerData.get(p.getUniqueId());
			
			// First check if the player just completed a parkour
			if (pd.hasCompletedParkour()) {
				Parkour pk = parkours.get(pd.getCurrentPkId());
				int newPos = pk.checkNewTime(p.getName(), pd.getParkourTime());
				if (newPos != -1) {
					getServer().broadcastMessage(prefix + p.getDisplayName() + ChatColor.AQUA + " is now " + ChatColor.RED + (newPos + 1) 
							+ "." + ChatColor.AQUA + " in " + ChatColor.GOLD + pk.getName() + ChatColor.AQUA + " with " + ChatColor.GREEN + pd.getParkourTime() + " secs.");
					Statement stmt;
					try {
						stmt = conn.createStatement();
						for (int i = 0; i < pk.getHighscores().size(); i++) {
							ParkourHighscore hs = pk.getHighscores().get(i);
							stmt.executeUpdate("INSERT INTO highscores (parkour_id, position, player_name, time) VALUES (" + pk.getId() + "," + i + ",'" 
									+ hs.getPlayerName() + "'," + hs.getTime() + ") ON DUPLICATE KEY UPDATE player_name = '" + hs.getPlayerName() + "', time = " + hs.getTime());
						}
						stmt.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
				pd.reset();
			} else {	// Else handle all parkours
				for (Parkour pk : parkours.values()) {
					// Skip if no start pos
					if (pk.getStartPosition() == null)
						continue;
					
					// Skip if already on start square and haven't started yet
					if (pd.isInParkour() && !pd.hasStartedParkour())
						continue;
					
					// Check if on start square
					if (p.getLocation().getWorld().getUID().equals(pk.getStartPosition().getWorld().getUID()) && 
							p.getLocation().getBlockX() == pk.getStartPosition().getBlockX() && 
							p.getLocation().getBlockY() == pk.getStartPosition().getBlockY() && 
							p.getLocation().getBlockZ() == pk.getStartPosition().getBlockZ()) {
						p.sendMessage(prefix + ChatColor.AQUA + "You are in the start zone for " + ChatColor.GOLD + pk.getName());
						p.sendMessage(prefix + ChatColor.AQUA + "To start the parkour just start running");
						p.sendMessage(prefix + ChatColor.AQUA + "If you fail return to the start to try again,");
						p.sendMessage(prefix + ChatColor.AQUA + "or type " + ChatColor.RED + "/pk cancel" + ChatColor.AQUA + " to cancel parkouring");
						
						pd.reset();
						pd.enterParkour(pk.getId());	
						break;
					}
				}
			}
		}
	}
}
