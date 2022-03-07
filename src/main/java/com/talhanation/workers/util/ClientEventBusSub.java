package com.talhanation.workers.util;


import com.talhanation.workers.Main;
import com.talhanation.workers.client.render.FishermanRenderer;
import com.talhanation.workers.client.render.MerchantRenderer;
import com.talhanation.workers.client.render.MinerRenderer;
import com.talhanation.workers.client.render.WorkersRenderer;
import com.talhanation.workers.init.ModEntityTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Main.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD , value = Dist.CLIENT)
public class ClientEventBusSub {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event){
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.MINER.get(), MinerRenderer::new );
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.LUMBERJACK.get(), WorkersRenderer::new );
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.SHEPHERD.get(), WorkersRenderer::new );
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.FARMER.get(), WorkersRenderer::new );
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.FISHERMAN.get(), FishermanRenderer::new );
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.MERCHANT.get(), MerchantRenderer::new );

    }
}