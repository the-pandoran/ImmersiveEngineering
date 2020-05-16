/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.client.render.tile;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.api.IEProperties.IEObjState;
import blusunrize.immersiveengineering.api.IEProperties.VisibilityList;
import blusunrize.immersiveengineering.client.ClientUtils;
import blusunrize.immersiveengineering.client.models.IOBJModelCallback;
import blusunrize.immersiveengineering.client.models.obj.IESmartObjModel;
import blusunrize.immersiveengineering.client.render.tile.DynamicModel.ModelType;
import blusunrize.immersiveengineering.client.utils.SinglePropertyModelData;
import blusunrize.immersiveengineering.common.IEConfig;
import blusunrize.immersiveengineering.common.blocks.IEBlocks.Multiblocks;
import blusunrize.immersiveengineering.common.blocks.metal.BucketWheelTileEntity;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.dummy.GlStateManager;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.data.IModelData;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class BucketWheelRenderer extends TileEntityRenderer<BucketWheelTileEntity>
{
	private final DynamicModel<Void> wheel = DynamicModel.createSimple(
			new ResourceLocation(ImmersiveEngineering.MODID, "block/metal_multiblock/bucket_wheel.obj.ie"),
			"bucket_wheel", ModelType.IE_OBJ);

	public BucketWheelRenderer(TileEntityRendererDispatcher rendererDispatcherIn)
	{
		super(rendererDispatcherIn);
	}

	@Override
	public void render(BucketWheelTileEntity tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer bufferIn, int combinedLightIn, int combinedOverlayIn)
	{
		if(!tile.formed||!tile.getWorldNonnull().isBlockLoaded(tile.getPos())||tile.isDummy())
			return;
		final BlockRendererDispatcher blockRenderer = Minecraft.getInstance().getBlockRendererDispatcher();
		BlockState state = tile.getWorldNonnull().getBlockState(tile.getPos());
		if(state.getBlock()!=Multiblocks.bucketWheel)
			return;
		IBakedModel model = wheel.get(null);
		Map<String, String> texMap = new HashMap<>();
		List<String> list = Lists.newArrayList("bucketWheel");
		synchronized(tile.digStacks)
		{
			for(int i = 0; i < tile.digStacks.size(); i++)
				if(!tile.digStacks.get(i).isEmpty())
				{
					list.add("dig"+i);
					Block b = Block.getBlockFromItem(tile.digStacks.get(i).getItem());
					BlockState digState = b!=Blocks.AIR?b.getDefaultState(): Blocks.COBBLESTONE.getDefaultState();
					IBakedModel digModel = Minecraft.getInstance().getBlockRendererDispatcher().getBlockModelShapes().getModel(digState);
					digModel.getParticleTexture();
					texMap.put("dig"+i, digModel.getParticleTexture().getName().toString());
				}
		}
		IEObjState objState = new IEObjState(VisibilityList.show(list));

		Tessellator tessellator = Tessellator.getInstance();
		matrixStack.push();

		matrixStack.translate(.5, .5, .5);
		GlStateManager.blendFunc(770, 771);
		GlStateManager.enableBlend();
		GlStateManager.disableCull();
		Direction facing = tile.getFacing();
		if(tile.getIsMirrored())
		{
			matrixStack.scale(facing.getAxis()==Axis.X?-1: 1, 1, facing.getAxis()==Axis.Z?-1: 1);
			GlStateManager.disableCull();
		}
		float dir = tile.getFacing()==Direction.SOUTH?90: tile.getFacing()==Direction.NORTH?-90: tile.getFacing()==Direction.EAST?180: 0;
		matrixStack.rotate(new Quaternion(new Vector3f(0, 1, 0), dir, true));
		float rot = tile.rotation+(float)(tile.active?IEConfig.MACHINES.excavator_speed.get()*partialTicks: 0);
		matrixStack.rotate(new Quaternion(new Vector3f(1, 0, 0), rot, true));

		RenderHelper.disableStandardItemLighting();
		Minecraft.getInstance().textureManager.bindTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE);
		BufferBuilder worldRenderer = tessellator.getBuffer();
		worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
		matrixStack.translate(-.5, -.5, -.5);
//		IModelData modelData = new SinglePropertyModelData<>(objState, Model.IE_OBJ_STATE);
		IModelData modelData = new SinglePropertyModelData<>(tile, IOBJModelCallback.PROPERTY);
		List<BakedQuad> quads;
		if(model instanceof IESmartObjModel)
			quads = ((IESmartObjModel)model).getQuads(state, null, 0, objState, texMap, true,
					modelData);
		else
			quads = model.getQuads(state, null, Utils.RAND, modelData);
		ClientUtils.renderModelTESRFast(quads, worldRenderer, tile.getWorldNonnull(), tile.getPos());
		matrixStack.translate(0.5, 0.5, 0.5);
		tessellator.draw();
		matrixStack.pop();
		RenderHelper.enableStandardItemLighting();
		GlStateManager.disableBlend();
		GlStateManager.enableCull();
	}
}