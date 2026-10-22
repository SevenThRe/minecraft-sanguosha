package dev.sgs.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public final class TableBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape NORTH = makeShape();
    private static final VoxelShape EAST = rotateShape(NORTH);
    private static final VoxelShape SOUTH = rotateShape(EAST);
    private static final VoxelShape WEST = rotateShape(SOUTH);
    public TableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }

    private static VoxelShape makeShape() {
        VoxelShape shape=Block.box(-6,12.9,2,22,14.1,14);
        for(int i=0;i<12;i++) {
            double inset=3-i*.25,z=-1+i*.25;
            shape=Shapes.or(shape,Block.box(-6+inset,12.9,z,22-inset,14.1,z+.25),
                    Block.box(-6+inset,12.9,16-z-.25,22-inset,14.1,16-z));
        }
        for(double x:new double[]{-2.5,16.5})for(double z:new double[]{2,12})
            shape=Shapes.or(shape,Block.box(x-.2,10.1,z-.2,x+2.2,12.9,z+2.2),
                    Block.box(x+.15,.6,z+.15,x+1.85,10.1,z+1.85),Block.box(x,.2,z,x+2,.6,z+2));
        return Shapes.or(shape,
                Block.box(-2.5,11.6,2,18.5,12.9,2.65),Block.box(-2.5,11.6,13.35,18.5,12.9,14),
                Block.box(-2.5,11.6,2.65,-1.85,12.9,13.35),Block.box(17.85,11.6,2.65,18.5,12.9,13.35),
                Block.box(-1.5,3.1,7.65,17.5,3.75,8.35),Block.box(-1.85,3.1,3,-1.15,3.75,13),
                Block.box(17.15,3.1,3,17.85,3.75,13)).optimize();
    }

    private static VoxelShape rotateShape(VoxelShape source) {
        VoxelShape[] result={Shapes.empty()};
        source.forAllBoxes((x0,y0,z0,x1,y1,z1)->result[0]=Shapes.or(result[0],
                Shapes.box(1-z1,y0,x0,1-z0,y1,x1)));
        return result[0].optimize();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }

    @Override
    protected BlockState mirror(BlockState state,Mirror mirror) { return rotate(state,mirror.getRotation(state.getValue(FACING))); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch(state.getValue(FACING)) {case EAST->EAST;case SOUTH->SOUTH;case WEST->WEST;default->NORTH;};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) RoomService.open(serverPlayer, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
