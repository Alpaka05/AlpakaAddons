package net.alpaka.addons.features.snow;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SnowOverlayRenderer {
    private static final int PARTICLE_COUNT = 105;
    private static final List<Snowflake> snowflakes = new ArrayList<>();
    private static final Random random = new Random();
    private static long lastTime = System.currentTimeMillis();

    private static int lastScreenWidth = 0;
    private static int lastScreenHeight = 0;

    private static class Snowflake {
        float x;
        float y;
        int size;
        float speedX;
        float speedY;
        int alpha;

        Snowflake(int screenWidth, int screenHeight) {
            reset(screenWidth, screenHeight, true);
        }

        void reset(int screenWidth, int screenHeight, boolean initial) {
            float w = screenWidth > 0 ? screenWidth : 400;
            float h = screenHeight > 0 ? screenHeight : 300;

            float minX = -w * 0.6f;
            float maxX = w;

            this.speedX = 0.4f + random.nextFloat() * 0.8f;
            this.speedY = 0.8f + random.nextFloat() * 1.4f;
            this.size = random.nextInt(3) + 1;
            this.alpha = 100 + random.nextInt(130);

            if (initial) {
                this.y = random.nextFloat() * h;
                this.x = minX + random.nextFloat() * (maxX - minX);
            } else {
                this.y = -10;
                this.x = minX + random.nextFloat() * (maxX - minX);
            }
        }
    }

    private static void ensureParticles(int width, int height) {
        if (snowflakes.size() < PARTICLE_COUNT) {
            for (int i = snowflakes.size(); i < PARTICLE_COUNT; i++) {
                snowflakes.add(new Snowflake(width, height));
            }
        }
    }

    public static void render(GuiGraphicsExtractor graphicsExtractor, int width, int height) {
        if (width <= 0 || height <= 0) return;

        ensureParticles(width, height);

        // Handle window resize dynamically to prevent empty spaces when going fullscreen
        if (width != lastScreenWidth || height != lastScreenHeight) {
            if (lastScreenWidth > 0 && lastScreenHeight > 0) {
                float scaleX = (float) width / (float) lastScreenWidth;
                float scaleY = (float) height / (float) lastScreenHeight;
                for (Snowflake flake : snowflakes) {
                    flake.x *= scaleX;
                    flake.y *= scaleY;
                }
            }
            lastScreenWidth = width;
            lastScreenHeight = height;
        }

        long currentTime = System.currentTimeMillis();
        float dt = Math.min((currentTime - lastTime) / 1000.0f, 0.1f);
        lastTime = currentTime;

        float speedMultiplier = AlpakaConfig.instance.inventorySnowSpeed;

        for (Snowflake flake : snowflakes) {
            flake.x += flake.speedX * 40.0f * speedMultiplier * dt;
            flake.y += flake.speedY * 40.0f * speedMultiplier * dt;

            if (flake.y > height + 10 || flake.x > width + width * 0.6f) {
                flake.reset(width, height, false);
            }

            int posX = Math.round(flake.x);
            int posY = Math.round(flake.y);
            int color = (flake.alpha << 24) | 0xFFFFFF;

            graphicsExtractor.fill(posX, posY, posX + flake.size, posY + flake.size, color);
        }
    }
}
