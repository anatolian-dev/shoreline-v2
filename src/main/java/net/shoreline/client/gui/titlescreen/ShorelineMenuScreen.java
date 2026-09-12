package net.shoreline.client.gui.titlescreen;

import lombok.Getter;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.shoreline.client.Shoreline;
import net.shoreline.client.gui.clickgui.ClickGuiScreen;
import net.shoreline.client.gui.titlescreen.snow.SnowField;
import net.shoreline.client.impl.module.client.ClickGuiModule;
import net.shoreline.client.impl.module.client.TitleScreenModule;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Getter
public class ShorelineMenuScreen extends Screen
{
    private final List<MenuButton> buttons;
    private static SnowField snowField;
    private final ClickGuiScreen clickGuiScreen = ClickGuiScreen.INSTANCE;
    private boolean renderingGui;

    public ShorelineMenuScreen()
    {
        super(Text.of("Shoreline-MainMenu"));
        buttons = new ArrayList<>();
    }

    @Override
    public void resize(int width, int height)
    {
        super.resize(width, height);
        resetButtons();
    }

    @Override
    protected void init()
    {
        super.init();
        if (snowField == null)
        {
            snowField = TitleScreenModule.INSTANCE.createField();
        }

        resetButtons();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0xFF000000);
        snowField.update(context.getScaledWindowWidth(), context.getScaledWindowHeight());
        snowField.render(context);

        double mX = renderingGui ? -1 : mouseX;
        double mY = renderingGui ? -1 : mouseY;
        for (MenuButton button : buttons)
        {
            button.render(context, mX, mY, delta);
        }

        if (renderingGui)
        {
            clickGuiScreen.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(Click click,
                                boolean bl)
    {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (renderingGui)
        {
            clickGuiScreen.mouseClicked(click, bl);
        }
        else
        {
            buttons.forEach(menuButton -> menuButton.mouseClicked(mouseX, mouseY, button));
        }

        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(Click click)
    {
        if (renderingGui)
        {
            clickGuiScreen.mouseReleased(click);
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        if (renderingGui)
        {
            clickGuiScreen.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input)
    {
        int keyCode = input.key();
        if (keyCode == ClickGuiModule.INSTANCE.getKeybind().getValue().getKeycode())
        {
            renderingGui = true;
            Window window = client.getWindow();
            ClickGuiModule.INSTANCE.setFadeState(true);
            clickGuiScreen.init(window.getScaledWidth(), window.getScaledHeight());
        }
        else if (keyCode == GLFW.GLFW_KEY_ESCAPE)
        {
            renderingGui = false;
            clickGuiScreen.reset();
        }
        else if (renderingGui)
        {
            clickGuiScreen.keyPressed(input);
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input)
    {
        if (renderingGui)
        {
            clickGuiScreen.charTyped(input);
        }

        return super.charTyped(input);
    }

    @Override
    public boolean shouldCloseOnEsc()
    {
        return false;
    }

    public void resetButtons()
    {
        buttons.clear();
        Window window = client.getWindow();
        float scaledWidth  = window.getScaledWidth();
        float scaledHeight = window.getScaledHeight();
        float spacing = 10;

        List<MenuButton> allButtons = new ArrayList<>();
        allButtons.add(new MenuButton(I18n.translate("menu.singleplayer").toUpperCase(Locale.ROOT), () -> client.setScreen(new SelectWorldScreen(this)), 0, 0));
        allButtons.add(new MenuButton(I18n.translate("menu.multiplayer").toUpperCase(Locale.ROOT), () -> client.setScreen(new MultiplayerScreen(this)), 0, 0));
        allButtons.add(new MenuButton(I18n.translate("menu.options").toUpperCase(Locale.ROOT).replace(".", ""), () -> client.setScreen(new OptionsScreen(this, client.options)), 0, 0));

        if (hasIAS())
        {
            allButtons.add(new MenuButton("Accounts".toUpperCase(), () -> client.setScreen(getAccountScreen(this)), 0, 0));
        }

        allButtons.add(new MenuButton(I18n.translate("menu.quit").toUpperCase(Locale.ROOT), client::scheduleStop, 0, 0));

        float totalWidth = 0;
        for (MenuButton button : allButtons)
        {
            totalWidth += button.getWidth();
        }

        totalWidth += spacing * (allButtons.size() - 1);

        float startX = (scaledWidth - totalWidth) / 2;
        float centerY = (scaledHeight / 2) + 60;

        float currentX = startX;
        for (MenuButton button : allButtons)
        {
            buttons.add(new MenuButton(button.getName(), button.getRunnable(), currentX, centerY));
            currentX += button.getWidth() + spacing;
        }
    }

    public static void setSnowField(SnowField field)
    {
        snowField = field;
    }

    public boolean hasIAS()
    {
        try
        {
            Class.forName("ru.vidtu.ias.IASMinecraft");
            return true;
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
    }

    public Screen getAccountScreen(Screen parent)
    {
        try
        {
            String screenName = "ru.vidtu.ias.screen.AccountScreen";
            Class<?> screen = Class.forName(screenName);
            Constructor<?> ctr = screen.getDeclaredConstructor(Screen.class);
            ctr.setAccessible(true);
            return (Screen) ctr.newInstance(parent);
        }
        catch (ClassNotFoundException
               | InstantiationException
               | IllegalAccessException
               | InvocationTargetException
               | NoSuchMethodException e)
        {
            e.printStackTrace();
            return null;
        }
    }
}
