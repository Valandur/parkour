package parkour;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.mysql.jdbc.exceptions.jdbc4.CommunicationsException;
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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
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
	private BukkitTask taskScoreboard;
	
	
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
			Statement stmt = getStatement();
			Statement stmt2 = getStatement();
	        ResultSet rs = stmt.executeQuery("SELECT * FROM pk_parkours");
	        while (rs.next()) {
	        	try {
		        	Parkour pk = new Parkour(rs.getInt("ID"), rs.getString("Name"), rs.getInt("MaxHighscores"));
		        	if (rs.getString("StartWorld") != null)
		        		pk.setStartPos(new Location(Bukkit.getWorld(rs.getString("StartWorld")), rs.getInt("StartPosX"), rs.getInt("StartPosY"), rs.getInt("StartPosZ")));
		        	if (rs.getString("EndWorld") != null)
		        		pk.setEndPos(new Location(Bukkit.getWorld(rs.getString("EndWorld")), rs.getInt("EndPosX"), rs.getInt("EndPosY"), rs.getInt("EndPosZ")));
		        	parkours.put(pk.getId(), pk);
		        	
		        	ResultSet rs2 = stmt2.executeQuery("SELECT * FROM pk_highscores WHERE ParkourId = " + pk.getId() + " ORDER BY Position;");
		        	while (rs2.next()) {
		        		pk.getHighscores().add(new ParkourHighscore(rs2.getString("PlayerName"), rs2.getFloat("Time")));
		        	}
		        	rs2.close();
		        	
		        	rs2 = stmt2.executeQuery("SELECT * FROM pk_scoreboards WHERE ParkourId = " + pk.getId() + " ORDER BY ScoreboardId;");
		        	while (rs2.next()) {
		        		pk.setScoreboard(rs2.getInt("ScoreboardId"), 
		        				(Sign)Bukkit.getWorld(rs2.getString("SignNameWorld")).getBlockAt(rs2.getInt("SignNameX"), rs2.getInt("SignNameY"), rs2.getInt("SignNameZ")).getState(),  
		        				(Sign)Bukkit.getWorld(rs2.getString("SignScoreWorld")).getBlockAt(rs2.getInt("SignScoreX"), rs2.getInt("SignScoreY"), rs2.getInt("SignScoreZ")).getState());
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
		for (Player p : getServer().getOnlinePlayers()) {
			PlayerData pd = new PlayerData();
			playerData.put(p.getUniqueId(), pd);
		}
		
		logger.info(prefix + "Starting threads...");
		taskParkour = getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() { public void run() { onPkTick(); } }, 20, 20);
		taskScoreboard = getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() { public void run() { onScoreboardUpdate(); } }, 600, 600);
	}
	@Override
	public void onDisable() {
		if (taskParkour != null)
			taskParkour.cancel();
		if (taskScoreboard != null)
			taskScoreboard.cancel();
	}
    
    private void connectToDB() {
		try {
			conn = DriverManager.getConnection("jdbc:mysql://" + db_server + ":" + db_port + "/" + db_name + "?user=" + db_un + "&password=" + db_pw + "&connect-timeout=0");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	private Statement getStatement() {
        return getStatement(true);
    }
    private Statement getStatement(boolean retry) {
        try {
            return conn.createStatement();
        } catch (CommunicationsException e) {
            connectToDB();
            if (retry)
                return getStatement(false);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
	
	// Command
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(prefix + "The Parkour commands can only be used by players");
			return false;
		}
		
		if (commandLabel.equalsIgnoreCase("pk")) {
            Player p = (Player)sender;
            PlayerData pd = playerData.get(p.getUniqueId());
            
			if (args.length <= 0) {
				p.sendMessage(prefix + ChatColor.RED + "Command syntax");
				p.sendMessage(prefix + ChatColor.GOLD + "/pk [cmd]");
				p.sendMessage(prefix + ChatColor.GOLD + "  [cmd]");
				p.sendMessage(prefix + ChatColor.GOLD + "    add [name]");
				p.sendMessage(prefix + ChatColor.GOLD + "    list");
				p.sendMessage(prefix + ChatColor.GOLD + "    set [name]");
				p.sendMessage(prefix + ChatColor.GOLD + "    cancel");
				return false;
			}
            
            Statement stmt;
			if (args[0].equalsIgnoreCase("add")) {
                if (args.length <= 1) {
                    p.sendMessage(prefix + ChatColor.RED + "Command syntax: " + ChatColor.GOLD + "/pk add [name]");
                    return false;
                }
                
				// Composite name
				String name = args[1];
				for (int i = 2; i < args.length; i++)
					name += " " + args[i];
				
				// Create parkour
				try {
					stmt = getStatement();
					stmt.executeUpdate("INSERT INTO pk_parkours (Name) VALUES ('" + name + "');");
					ResultSet rs = stmt.executeQuery("SELECT * FROM pk_parkours WHERE Name = '" + name + "';");
					
					if (rs.next()) {
						Parkour pk = new Parkour(rs.getInt("Id"), name, rs.getInt("MaxHighscores"));
                        parkours.put(pk.getId(), pk);
						p.sendMessage(prefix + "Added parkour " + pk.getName() + " (" + pk.getId() + ")");
					}
					
					rs.close();
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} else if (args[0].equalsIgnoreCase("list")) {
                p.sendMessage(prefix + "Parkour list");
                for (Parkour park : parkours.values()) {
                    p.sendMessage(prefix + park.getId() + ": " + park.getName() + " (x: " + park.getStartPosition().getBlockX() + ", z: " + park.getStartPosition().getBlockZ() + ")");
                }
            } else if (args[0].equalsIgnoreCase("cancel")) {
				pd.reset();
				p.sendMessage(prefix + ChatColor.AQUA + "You have canceled your parkour run");
			} else if (args[0].equalsIgnoreCase("set")) {
				if (args.length <= 2) {
					p.sendMessage(prefix + ChatColor.RED + "Command syntax");
					p.sendMessage(prefix + ChatColor.GOLD + "/pk set [name] [cmd]");
					p.sendMessage(prefix + ChatColor.GOLD + "  [cmd]");
					p.sendMessage(prefix + ChatColor.GOLD + "    start");
					p.sendMessage(prefix + ChatColor.GOLD + "    end");
					p.sendMessage(prefix + ChatColor.GOLD + "    sb [id]");
					p.sendMessage(prefix + ChatColor.GOLD + "    min");
					p.sendMessage(prefix + ChatColor.GOLD + "    max");
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
					try {
						stmt = getStatement();
						stmt.executeUpdate("UPDATE pk_parkours SET StartWorld = '" + p.getLocation().getWorld().getName() 
								+ "', StartPosX = " + p.getLocation().getBlockX() 
								+ ", StartPosY = " + p.getLocation().getBlockY() 
								+ ", StartPosZ = " + p.getLocation().getBlockZ()
								+ " WHERE Id = " + pk.getId());
						stmt.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
					p.sendMessage(prefix + pk.getName() + " start position set");
				} else if (args[2].equalsIgnoreCase("end")) {
					pk.setEndPos(p.getLocation());
					try {
						stmt = getStatement();
						stmt.executeUpdate("UPDATE pk_parkours SET EndWorld = '" + p.getLocation().getWorld().getName() 
								+ "', EndPosX = " + p.getLocation().getBlockX() 
								+ ", EndPosY = " + p.getLocation().getBlockY() 
								+ ", EndPosZ = " + p.getLocation().getBlockZ()
								+ " WHERE Id = " + pk.getId());
						stmt.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
					p.sendMessage(prefix + pk.getName() + " end position set");
				} else if (args[2].equalsIgnoreCase("sb")) {
                    if (args.length <= 3) {
                        p.sendMessage(prefix + ChatColor.RED + "Command syntax: " + ChatColor.GOLD + "/pk set [name] sb [id]");
                        return false;
                    }
                    
					int sb = Integer.parseInt(args[3]);
					pd.editSb(pk.getId(), sb);
					p.sendMessage(prefix + ChatColor.AQUA + "Please select the player names sign for scoreboard " + ChatColor.GOLD + "#" + sb + ChatColor.AQUA + " for " + ChatColor.GOLD + pk.getName());
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
							Statement stmt = getStatement();
							for (int key : pk.getHighscoreScoreboards().keySet()) {
								ParkourScoreboard sb = pk.getHighscoreScoreboards().get(key);
								stmt.executeUpdate("INSERT INTO pk_scoreboards (ParkourId, ScoreboardId, SignNameWorld, SignNameX, SignNameY, SignNameZ, "
										+ "SignScoreWorld, SignScoreX, SignScoreY, SignScoreZ) VALUES (" + pk.getId() + "," + key + ",'" 
										+ sb.getNameSign().getWorld().getName() + "'," + sb.getNameSign().getX() + "," + sb.getNameSign().getY() + "," + sb.getNameSign().getZ() + ",'" 
										+ sb.getScoreSign().getWorld().getName() + "'," + sb.getScoreSign().getX() + "," + sb.getScoreSign().getY() + "," + sb.getScoreSign().getZ() 
										+ ") ON DUPLICATE KEY UPDATE SignNameWorld='" + sb.getNameSign().getWorld().getName() + "',SignNameX=" + sb.getNameSign().getX() 
										+ ",SignNameY=" + sb.getNameSign().getY() + ",SignNameZ=" + sb.getNameSign().getZ() + ",SignScoreWorld='" + sb.getScoreSign().getWorld().getName() 
										+ "',SignScoreX=" + sb.getScoreSign().getX() + ",SignScoreY=" + sb.getScoreSign().getY() + ",SignScoreZ=" + sb.getScoreSign().getZ());
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
	@EventHandler
	public void onPlayerTeleport(PlayerTeleportEvent e) {
		Player p = e.getPlayer();
		PlayerData pd = playerData.get(p.getUniqueId());
		
		if (pd.isInParkour()) {
			Parkour pk = parkours.get(pd.getCurrentPkId());
			
			if (pd.hasStartedParkour() && !pd.hasCompletedParkour() && e.getCause() != TeleportCause.UNKNOWN) {
				pd.reset();
				p.sendMessage(prefix + ChatColor.AQUA + "You tried to teleport in " + pk.getName() + ", run canceled");
				return;
			}
		}
	}
	
	// Other
	public void onPkTick() {
		for (Player p : getServer().getOnlinePlayers()) {
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
						stmt = getStatement();
						for (int i = 0; i < pk.getHighscores().size(); i++) {
							ParkourHighscore hs = pk.getHighscores().get(i);
							stmt.executeUpdate("INSERT INTO pk_highscores (ParkourId, Position, PlayerName, Time) VALUES (" + pk.getId() + "," + i + ",'" 
									+ hs.getPlayerName() + "'," + hs.getTime() + ") ON DUPLICATE KEY UPDATE PlayerName = '" + hs.getPlayerName() + "', Time = " + hs.getTime());
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
	public void onScoreboardUpdate() {
		for (Parkour p : parkours.values()) {
			p.updateScoreboards();
		}
	}
}
