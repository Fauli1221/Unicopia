package com.minelittlepony.unicopia.client.gui;

import org.lwjgl.opengl.GL11;

import com.minelittlepony.common.client.gui.element.Button;
import com.minelittlepony.unicopia.Unicopia;
import com.minelittlepony.unicopia.container.SpellBookContainer;
import com.minelittlepony.unicopia.container.SpellBookContainer.SpellbookSlot;
import com.minelittlepony.unicopia.enchanting.IPageUnlockListener;
import com.minelittlepony.unicopia.enchanting.Page;
import com.minelittlepony.unicopia.enchanting.PageState;
import com.minelittlepony.unicopia.enchanting.Pages;
import com.minelittlepony.unicopia.entity.player.Pony;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.gui.screen.ingame.ContainerScreen;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.container.Slot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.PacketByteBuf;

public class SpellBookScreen extends ContainerScreen<SpellBookContainer> implements IPageUnlockListener {

    private static Page currentPage;

    public static final Identifier TEXTURE = new Identifier("unicopia", "textures/gui/container/book.png");

    private Pony player;

    private PageButton nextPage;
    private PageButton prevPage;

    public SpellBookScreen(int sync, Identifier id, PlayerEntity player, PacketByteBuf buf) {
        super(new SpellBookContainer(sync, id, player, buf), player.inventory, buf.readText());

        containerWidth = 405;
        containerHeight = 219;
        this.player = Pony.of(player);
    }

    @Override
    public void init() {
        super.init();

        addButton(nextPage = new PageButton(x + 360, y + 185, true)).onClick(v -> {
            currentPage = currentPage.next();
            onPageChange();
        });
        addButton(prevPage = new PageButton(x + 20, y + 185, false)).onClick(v -> {
            currentPage = currentPage.prev();
            onPageChange();
        });

        if (currentPage == null) {
            currentPage = Pages.instance().getByIndex(0);
        }

        onPageChange();

        if (player.getPages().hasPageStateRelative(currentPage, PageState.UNREAD, Page::next)) {
            nextPage.triggerShake();
        }

        if (player.getPages().hasPageStateRelative(currentPage, PageState.UNREAD, Page::prev)) {
            prevPage.triggerShake();
        }
    }

    protected void onPageChange() {
        prevPage.setEnabled(currentPage.getIndex() > 0);
        nextPage.setEnabled(currentPage.getIndex() < Pages.instance().getTotalPages() - 1);

        if (player.getPages().getPageState(currentPage) == PageState.UNREAD) {
            player.getPages().setPageState(currentPage, PageState.READ);
        }
    }

    @Override
    public boolean onPageUnlocked(Page page) {
        int i = currentPage.compareTo(page);

        if (i <= 0) {
            prevPage.triggerShake();
        }

        if (i >= 0) {
            nextPage.triggerShake();
        }

        return true;
    }

    @Override
    protected void fillGradient(int left, int top, int width, int height, int startColor, int endColor) {
        if (focusedSlot == null || left != focusedSlot.xPosition || top != focusedSlot.yPosition || !drawSlotOverlay(focusedSlot)) {
            super.fillGradient(left, top, width, height, startColor, endColor);
        }
    }

    protected boolean drawSlotOverlay(Slot slot) {
        if (slot instanceof SpellbookSlot) {
            GlStateManager.enableBlend();
            GL11.glDisable(GL11.GL_ALPHA_TEST);

            minecraft.getTextureManager().bindTexture(TEXTURE);
            blit(slot.xPosition - 1, slot.yPosition - 1, 74, 223, 18, 18, 512, 256);

            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GlStateManager.disableBlend();

            return true;
        }

        return false;
    }


    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        super.render(mouseX, mouseY, partialTicks);
        drawMouseoverTooltip(mouseX, mouseY);
    }

    @Override
    protected void drawForeground(int mouseX, int mouseY) {
        String text = String.format("%d / %d", currentPage.getIndex() + 1, Pages.instance().getTotalPages());

        font.draw(text, 70 - font.getStringWidth(text)/2, 190, 0x0);
    }


    @Override
    protected void drawBackground(float partialTicks, int mouseX, int mouseY) {
        renderBackground(0);
        GlStateManager.color4f(1, 1, 1, 1);

        int left = (width - containerWidth) / 2;
        int top = (height - containerHeight) / 2;

        minecraft.getTextureManager().bindTexture(TEXTURE);
        blit(left, top, 0, 0, containerWidth, containerHeight, 512, 256);

        GlStateManager.enableBlend();
        GL11.glDisable(GL11.GL_ALPHA_TEST);

        minecraft.getTextureManager().bindTexture(TEXTURE);
        blit(left + 147, top + 49, 407, 2, 100, 101, 512, 256);

        if (player.getPages().getPageState(currentPage) != PageState.LOCKED) {
            Identifier texture = currentPage.getTexture();

            if (minecraft.getTextureManager().getTexture(texture) != MissingSprite.getMissingSpriteTexture()) {
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                minecraft.getTextureManager().bindTexture(texture);
                blit(left, top, 0, 0, containerWidth, containerHeight, 512, 256);
            } else {
                if (player.getWorld().random.nextInt(100) == 0) {
                    Unicopia.LOGGER.fatal("Missing texture " + texture);
                }
            }
        }

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GlStateManager.disableBlend();
    }

    class PageButton extends Button {
        private final boolean direction;

        private int shakesLeft = 0;
        private float shakeCount = 0;

        public PageButton(int x, int y, boolean direction) {
            super(x, y, 23, 13);
            this.direction = direction;
        }

        @Override
        public void renderButton(int mouseX, int mouseY, float partialTicks) {
            if (visible) {

                boolean shaking = false;

                int x = this.x;
                int y = this.y;

                if (shakesLeft > 0) {
                    shaking = true;
                    shakeCount += (float)Math.PI / 2;

                    if (shakeCount >= Math.PI * 2) {
                        shakeCount %= Math.PI * 2;
                        shakesLeft--;
                    }

                    x += (int)(Math.sin(shakeCount) * 3);
                    y -= (int)(Math.sin(shakeCount) * 3);
                }

                GlStateManager.color4f(1, 1, 1, 1);
                minecraft.getTextureManager().bindTexture(TEXTURE);

                int u = 0;
                int v = 220;

                if (shaking || isMouseOver(mouseX, mouseY)) {
                    u += 23;
                }

                if (shaking) {
                    u += 23;
                }

                if (!direction) {
                    v += 13;
                }

                blit(x, y, u, v, 23, 13, 512, 256);
            }
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return visible &&  mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

        }

        public void triggerShake() {
            if (shakesLeft <= 0) {
                shakesLeft = 5;
            }
        }
    }
}
