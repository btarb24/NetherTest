package com.github.btarb24.NetherTest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EvtHandler implements Listener
{
	NetherTest _nether;
	public EvtHandler(NetherTest nether)
	{
		nether.getServer().getPluginManager().registerEvents(this, nether);
		_nether = nether; 
	}
	
	@EventHandler
	public void OnPlayerDeath(PlayerDeathEvent event)
	{
		//check if they were in the nether.  kill their session if they were. 
		//they will respawn in main world and not be able to get back in nether
		if (event.getEntity().getWorld().getName().equalsIgnoreCase(NetherTest.NETHER_SERVER_NAME))
			_nether.endNetherSession(event.getEntity());
	}
	
	@EventHandler
	public void OnPlayerQuit(PlayerQuitEvent event)
	{
		//persists the player's used nether minutes if they logout while in nether
		if (event.getPlayer().getWorld().getName().equals(NetherTest.NETHER_SERVER_NAME))
			_nether.logoutWhileInNether(event.getPlayer());
	}
}
