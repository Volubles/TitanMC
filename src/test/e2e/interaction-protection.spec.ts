import { test, waitForStable, waitUntil } from '@drownek/paperwright';
import { Vec3 } from 'vec3';

const chestPosition = new Vec3(5, 65, 1);
const bucketSupportPosition = new Vec3(7, 64, 1);
const bucketTargetPosition = new Vec3(7, 65, 1);
const waterPosition = new Vec3(9, 65, 1);

test('protected worlds deny containers and bucket changes', async ({ player, server }) => {
  await player.makeOp();
  await player.teleport(7, 67, 3);

  server.execute('setblock 5 65 1 chest');
  server.execute('setblock 7 64 1 stone');
  server.execute('setblock 7 65 1 air');
  server.execute('setblock 9 65 1 water');
  await player.deOp();

  await waitUntil(
    () => player.bot.blockAt(chestPosition)?.name === 'chest'
      && player.bot.blockAt(bucketSupportPosition)?.name === 'stone'
      && player.bot.blockAt(bucketTargetPosition)?.name === 'air'
      && player.bot.blockAt(waterPosition)?.name === 'water',
    { message: 'Expected interaction fixtures to reach the bot' },
  );

  const chest = player.bot.blockAt(chestPosition);
  if (!chest) throw new Error('Chest fixture was not loaded');
  void player.bot.activateBlock(chest).catch(() => undefined);
  await waitForStable(
    () => player.bot.currentWindow === null,
    { duration: 500, message: 'Expected protected chest to remain closed' },
  );

  await player.giveItem('water_bucket');
  const waterBucket = player.inventory.items().find(item => item.name === 'water_bucket');
  if (!waterBucket) throw new Error('Water bucket was not added to the inventory');
  await player.bot.equip(waterBucket, 'hand');

  const support = player.bot.blockAt(bucketSupportPosition);
  if (!support) throw new Error('Bucket support fixture was not loaded');
  void player.bot.activateBlock(support, new Vec3(0, 1, 0)).catch(() => undefined);
  await waitForStable(
    () => player.bot.blockAt(bucketTargetPosition)?.name === 'air',
    { duration: 500, message: 'Expected bucket emptying to be denied' },
  );

  server.execute(`item replace entity ${player.username} weapon.mainhand with bucket`);
  await waitUntil(
    () => player.bot.heldItem?.name === 'bucket',
    { message: 'Expected an empty bucket in the main hand' },
  );

  const water = player.bot.blockAt(waterPosition);
  if (!water) throw new Error('Water fixture was not loaded');
  void player.bot.activateBlock(water).catch(() => undefined);
  await waitForStable(
    () => player.bot.blockAt(waterPosition)?.name === 'water',
    { duration: 500, message: 'Expected bucket filling to be denied' },
  );
});
