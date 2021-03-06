package com.immibis.captchalogue_sylladex;

import net.minecraft.container.Container;
import net.minecraft.container.SlotActionType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

public class FetchModusQueue extends FetchModusType {
	@Override
	public boolean forceRightClickOneItem() {
		return true;
	}
	
	@Override
	public boolean overridesGuiSlotVisualConnectivity() {
		return true;
	}
	
	@Override
	public int getBackgroundGroupForSlot(int slot) {
		if(slot == CaptchalogueMod.MODUS_SLOT)
			return BG_GROUP_MODUS;
		
		// don't merge the extractable slot with anything else
		if(slot == 0)
			return 0;
		
		return 1; // merged rest of hotbar and main inventory
	}
	
	@Override
	public FetchModusState createFetchModusState(InventoryWrapper inv) {
		return new State(inv);
	}
	
	public static class State extends FetchModusState {
		private InventoryWrapper inv;
		public State(InventoryWrapper inv) {
			this.inv = inv;
		}
		
		@Override
		public void initialize() {
			compactItemsToLowerIndices(inv, 0, true);
		}
		
		@Override
		public boolean canTakeFromSlot(int slot) {
			return slot == 0;
		}
		@Override
		public boolean canInsertToSlot(int slot) {
			return inv.getInvStack(slot).isEmpty() && (slot == 0 || !inv.getInvStack(slot-1).isEmpty());
		}
		
		@Override
		public boolean hasCustomInsert() {
			return true;
		}
		
		private int getLastFilledSlot() {
			int lastFilled = 0; // return 0 if inventory is totally empty
			for(int k = 0; k < inv.getNumSlots(); k++) {
				if (!inv.getInvStack(k).isEmpty())
					lastFilled = k;
			}
			return lastFilled;
		}
		
		@Override
		public void insert(ItemStack stack, boolean allowViolentExpulsion) {
			ItemStack curStackLast = inv.getInvStack(getLastFilledSlot());
			if(!curStackLast.isEmpty() && Container.canStacksCombine(curStackLast, stack)) {
				int ntransfer = Math.min(stack.getCount(), curStackLast.getMaxCount() - curStackLast.getCount());
				curStackLast.increment(ntransfer);
				stack.decrement(ntransfer);
				if(stack.getCount() == 0)
					return;
			}
			for(int k = 0; k < inv.getNumSlots(); k++) {
				if (inv.getInvStack(k).isEmpty()) {
					inv.setInvStack(k, stack.copy());
					stack.setCount(0);
					return;
				}
			}
			
			if (allowViolentExpulsion) {
				// no free slots? launch whatever is at the head of the queue, shift the queue down, then push the new item.
				ItemStack launchItems = inv.getInvStack(0);
				if(launchItems.isEmpty())
					throw new AssertionError("unreachable - we have no empty slots, not even this one");
				inv.setInvStack(0, ItemStack.EMPTY);
				initialize(); // shift the queue down
				CaptchalogueMod.launchExcessItems(inv.getPlayer(), launchItems);
				insert(stack, false); // retry. Violent expulsion shouldn't happen again, but pass false just in case, to prevent infinite recursion.
			}
		}
		
		@Override
		protected boolean blocksAccessToHotbarSlot_(int slot) {
			return slot != 0;
		}

		@Override
		public boolean overrideInventoryClick(Container cont, PlayerInventory plinv, int slot, SlotActionType actionType, int clickData) {
			
			// A QuickCraft of one slot must be treated as a pickup, because it's very easy to drag over one slot, and it still gets counted as a QuickCraft.
			// We can't easily prevent QuickCraft from starting, but if we only make one slot insertable, then that's all the user can use for QuickCrafting.
			
			switch (actionType) {
			case CLONE: // copy stack in creative mode - allowed on any stack
				return false; // no override

			case PICKUP_ALL: // double-click on a slot to collect a stack of that item from anywhere in the inventory
				return false; // no override. It shouldn't take from blocked slots.

			case PICKUP: // normal click
				if(slot == 0)
					return false; // allow all forms of normal click in slot 0. This will only extract items, not insert them (unless it's the only slot) because of canTakeItems/canInsertItems
				
				// Insert items at the end of the queue by clicking anywhere other than slot 0 (even on slots that already have items). Right-click to insert one item.
				ItemStack cursor = plinv.getCursorStack();
				if(!cursor.isEmpty()) {
					if (clickData == 1 && cursor.getCount() >= 2) {
						// insert one item
						ItemStack one = cursor.copy();
						one.setCount(1);
						insert(one, true);
						if(one.isEmpty())
							cursor.decrement(1);
					} else {
						insert(cursor, true);
					}
				}
				return true;

			case QUICK_CRAFT:
				return false; // allow action ("quick craft" = dragging the mouse to select multiple slots; it can only put items into insertable slots, i.e. the first empty one)
				
			case SWAP:
				return true; // block action ("swap" = swap a hotbar slot 1-9 with the slot under the cursor)
				
			case QUICK_MOVE: // shift-click. Allowed on slot 0 to transfer the item into the other container.
				// On the inventory screen, this moves items from the hotbar into the player's non-hotbar inventory - which works fine. For a queue it cycles the queue, for a stack it does nothing, for others (memory) it might be useful.
				// It does tend to cause desyncs if you repeatedly click it. Not sure if that's a vanilla bug.
				// You wouldn't see that bug in vanilla, since shift-clicking clears the slot so you can't shift-click it again.
				if (slot == 0)
					return false;
				return true;
				
			case THROW: // throw item. Should only be allowed in extractable slots already.
				return false;
			}
			return true; // block all unknown kinds of clicks
		}
		
		@Override
		public void afterPossibleInventoryChange(long changedSlotMask, boolean serverSync) {
			// Brute force! :)
			if(!serverSync)
				initialize();
		}
	}
}