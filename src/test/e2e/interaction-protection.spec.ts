import { test, waitForStable, waitUntil } from '@drownek/paperwright';
import { Vec3 } from 'vec3';

const chestPosition = new Vec3(25, 65, 21);
const bucketSupportPosition = new Vec3(27, 64, 21);
const bucketTargetPosition = new Vec3(27, 65, 21);
const waterPosition = new Vec3(29, 65, 21);

test('protected worlds deny containers and bucket changes', async ({ player, server }) => {
  await player.makeOp();
  await player.teleport(27, 67, 23);

  server.execute('setblock 25 65 21 chest');
  server.execute('setblock 27 64 21 stone');
  server.execute('setblock 27 65 21 air');
  server.execute('setblock 29 65 21 water');
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
  await player.bot.activateBlock(chest);
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
  await player.bot.activateBlock(support, new Vec3(0, 1, 0));
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
  await player.bot.activateBlock(water);
  await waitForStable(
    () => player.bot.blockAt(waterPosition)?.name === 'water',
    { duration: 500, message: 'Expected bucket filling to be denied' },
  );
});
