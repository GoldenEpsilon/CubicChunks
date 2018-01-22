/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.asm.mixin.fixes.common.worldgen.tree;

import cubicchunks.world.ICubicWorld;
import cubicchunks.worldgen.generator.custom.populator.PopulatorUtils;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenHugeTrees;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.util.Random;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@Mixin(WorldGenHugeTrees.class)
public class MixinWorldGenHugeTrees {

    // the ordinal=0 is boolean flag = true
    @ModifyConstant(method = "isSpaceAt", constant = @Constant(intValue = 1, ordinal = 1))
    private int isSpace_getMinHeight(int val, World worldIn, BlockPos leavesPos, int height) {
        return ((ICubicWorld) worldIn).getMinHeight() + 1;
    }

    @ModifyConstant(method = "isSpaceAt", constant = @Constant(intValue = 256))
    private int isSpace_getMaxHeight(int val, World worldIn, BlockPos leavesPos, int height) {
        return ((ICubicWorld) worldIn).getMaxHeight();
    }

    @ModifyConstant(method = "isSpaceAt", constant = @Constant(
            intValue = 0,
            expandZeroConditions = Constant.Condition.LESS_THAN_ZERO,
            ordinal = 1))
    private int getMinScanHeight(int orig, World worldIn, BlockPos leavesPos, int height) {
        return ((ICubicWorld) worldIn).getMinHeight();
    }
}
