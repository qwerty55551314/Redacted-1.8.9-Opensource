package com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiVerticalList;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Clickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Closeable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Loadable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Tickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Consumer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public abstract class AbstractGuiResourceLoadingList<T extends AbstractGuiResourceLoadingList<T, U>, U extends GuiElement<U> & Comparable<U>>
   extends AbstractGuiVerticalList<T>
   implements Tickable,
   Loadable,
   Closeable,
   KeyHandler {
   private static final String[] LOADING_TEXT = new String[]{"Ooo", "oOo", "ooO", "oOo"};
   private final GuiLabel loadingElement = new GuiLabel();
   private final GuiPanel resourcesPanel = new GuiPanel(this.getListPanel()).setLayout(new VerticalLayout());
   private final Queue<Runnable> resourcesQueue = new ConcurrentLinkedQueue<>();
   private Consumer<Consumer<Supplier<U>>> onLoad;
   private Runnable onSelectionChanged;
   private Runnable onSelectionDoubleClicked;
   private Thread loaderThread;
   private int tick;
   private final List<AbstractGuiResourceLoadingList<T, U>.Element> selected = new ArrayList<>();
   private long selectedLastClickTime;

   public AbstractGuiResourceLoadingList() {
   }

   public AbstractGuiResourceLoadingList(GuiContainer container) {
      super(container);
   }

   @Override
   public void tick() {
      this.loadingElement.setText(LOADING_TEXT[this.tick++ / 5 % LOADING_TEXT.length]);

      Runnable resource;
      while ((resource = this.resourcesQueue.poll()) != null) {
         resource.run();
      }
   }

   @Override
   public void load() {
      if (this.loaderThread != null) {
         this.loaderThread.interrupt();

         try {
            this.loaderThread.join();
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
         }
      }

      this.resourcesQueue.clear();

      for (GuiElement element : new ArrayList<>(this.resourcesPanel.getChildren())) {
         this.resourcesPanel.removeElement(element);
      }

      this.selected.clear();
      this.onSelectionChanged();
      this.loaderThread = new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               AbstractGuiResourceLoadingList.this.onLoad.consume(new Consumer<Supplier<U>>() {
                  public void consume(Supplier<U> obj) {
                     AbstractGuiResourceLoadingList.this.resourcesQueue.offer(new Runnable() {
                        @Override
                        public void run() {
                           AbstractGuiResourceLoadingList.this.resourcesPanel.addElements(null, AbstractGuiResourceLoadingList.this.new Element(obj.get()));
                           AbstractGuiResourceLoadingList.this.resourcesPanel.sortElements();
                        }
                     });
                  }
               });
            } finally {
               AbstractGuiResourceLoadingList.this.resourcesQueue.offer(new Runnable() {
                  @Override
                  public void run() {
                     AbstractGuiResourceLoadingList.this.getListPanel().removeElement(AbstractGuiResourceLoadingList.this.loadingElement);
                  }
               });
            }
         }
      });
      this.getListPanel().addElements(new VerticalLayout.Data(0.5), this.loadingElement);
      this.loaderThread.start();
   }

   @Override
   public void close() {
      this.loaderThread.interrupt();
   }

   public T onLoad(Consumer<Consumer<Supplier<U>>> function) {
      this.onLoad = function;
      return this.getThis();
   }

   public void onSelectionChanged() {
      if (this.onSelectionChanged != null) {
         this.onSelectionChanged.run();
      }
   }

   public void onSelectionDoubleClicked() {
      if (this.onSelectionDoubleClicked != null) {
         this.onSelectionDoubleClicked.run();
      }
   }

   public T onSelectionChanged(Runnable onSelectionChanged) {
      this.onSelectionChanged = onSelectionChanged;
      return this.getThis();
   }

   public T onSelectionDoubleClicked(Runnable onSelectionDoubleClicked) {
      this.onSelectionDoubleClicked = onSelectionDoubleClicked;
      return this.getThis();
   }

   public List<U> getSelected() {
      List<U> selectedResources = new ArrayList<>(this.selected.size());

      for (AbstractGuiResourceLoadingList<T, U>.Element element : this.selected) {
         selectedResources.add((U)element.resource);
      }

      return selectedResources;
   }

   @Override
   public boolean handleKey(KeyInput keyInput) {
      if (keyInput.hasCtrl() && keyInput.key == 30) {
         List<AbstractGuiResourceLoadingList<T, U>.Element> all = new ArrayList<>();

         for (GuiElement<?> child : this.getListPanel().getChildren()) {
            if (child instanceof AbstractGuiResourceLoadingList.Element) {
               all.add((AbstractGuiResourceLoadingList.Element)child);
            }
         }

         if (this.selected.size() < all.size()) {
            this.selected.clear();
            this.selected.addAll(all);
         } else {
            this.selected.clear();
         }

         this.onSelectionChanged();
         return true;
      } else {
         return false;
      }
   }

   private class Element extends GuiPanel implements Clickable, Comparable<AbstractGuiResourceLoadingList<T, U>.Element> {
      private final GuiElement resource;

      public Element(final U resource) {
         this.resource = resource;
         this.addElements(null, resource);
         this.setLayout(new CustomLayout<GuiPanel>() {
            protected void layout(GuiPanel container, int width, int height) {
               this.pos(resource, 2, 2);
            }

            @Override
            public ReadableDimension calcMinSize(GuiContainer<?> container) {
               ReadableDimension size = resource.getMinSize();
               return new Dimension(size.getWidth() + 4, size.getHeight() + 4);
            }
         });
      }

      @Override
      public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
         if (renderInfo.layer == 0 && AbstractGuiResourceLoadingList.this.selected.contains(this)) {
            int w = size.getWidth();
            int h = size.getHeight();
            renderer.drawRect(0, 0, w, h, Colors.BLACK);
            renderer.drawRect(0, 0, w, 1, Colors.LIGHT_GRAY);
            renderer.drawRect(0, h - 1, w, 1, Colors.LIGHT_GRAY);
            renderer.drawRect(0, 0, 1, h, Colors.LIGHT_GRAY);
            renderer.drawRect(w - 1, 0, 1, h, Colors.LIGHT_GRAY);
         }

         super.draw(renderer, size, renderInfo);
      }

      @Override
      public boolean mouseClick(Click click) {
         Point point = new Point(click);
         this.getContainer().convertFor(this, point);
         if (point.getX() > 0 && point.getX() < this.getLastSize().getWidth() && point.getY() > 0 && point.getY() < this.getLastSize().getHeight()) {
            if (click.hasCtrl()) {
               if (AbstractGuiResourceLoadingList.this.selected.contains(this)) {
                  AbstractGuiResourceLoadingList.this.selected.remove(this);
               } else {
                  AbstractGuiResourceLoadingList.this.selected.add(this);
               }

               AbstractGuiResourceLoadingList.this.onSelectionChanged();
            } else if (AbstractGuiResourceLoadingList.this.selected.contains(this)
               && System.currentTimeMillis() - AbstractGuiResourceLoadingList.this.selectedLastClickTime < 250L) {
               AbstractGuiResourceLoadingList.this.onSelectionDoubleClicked();
            } else {
               AbstractGuiResourceLoadingList.this.selected.clear();
               AbstractGuiResourceLoadingList.this.selected.add(this);
               AbstractGuiResourceLoadingList.this.onSelectionChanged();
            }

            AbstractGuiResourceLoadingList.this.selectedLastClickTime = System.currentTimeMillis();
            return true;
         } else {
            return false;
         }
      }

      public int compareTo(AbstractGuiResourceLoadingList<T, U>.Element o) {
         return ((Comparable)this.resource).compareTo(o.resource);
      }
   }
}
