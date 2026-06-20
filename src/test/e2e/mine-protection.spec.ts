import { expect, test, waitForStable, waitUntil } from '@drownek/paperwright';
import { Vec3 } from 'vec3';

const inside = new Vec3(1, 65, 1);
const outside = new Vec3(5, 65, 1);

test('mine regions allow mining while the protected world denies outside breaks', async ({ player, server }) => {
  await player.makeOp();
  await player.teleport(1, 67, 3);

  player.chat('//pos1 0,64,0');
  await expect(player).toHaveReceivedMessage('First position set to');
  player.chat('//pos2 2,66,2');
  await expect(player).toHaveReceivedMessage('Second position set to');
  player.chat('/mine create alpha');
  await expect(player).toHaveReceivedMessage("Created mine 'alpha'");

  if (player.bot.currentWindow) {
    player.bot.closeWindow(player.bot.currentWindow);
  }

  server.execute('fill 0 64 0 2 66 2 dirt');
  server.execute('setblock 5 65 1 dirt');
  await player.deOp();
  await waitUntil(
    () => player.bot.blockAt(inside)?.name === 'dirt'
      && player.bot.blockAt(outside)?.name === 'dirt',
    { message: 'Expected test blocks to reach the bot before digging' },
  );

  const mineBlock = player.bot.blockAt(inside);
  if (!mineBlock) throw new Error('Mine block was not loaded by the bot');
  await player.bot.dig(mineBlock, true);
  await waitUntil(
    () => player.bot.blockAt(inside)?.name === 'air',
    { message: 'Expected block breaking to be allowed inside the mine' },
  );

  await player.teleport(5, 67, 3);
  const protectedBlock = player.bot.blockAt(outside);
  if (!protectedBlock) throw new Error('Protected block was not loaded by the bot');
  const deniedDig = player.bot.dig(protectedBlock, true);
  void deniedDig.catch(() => undefined);
  try {
    await waitForStable(
      () => player.bot.blockAt(outside)?.name === 'dirt',
      {
        duration: 500,
        timeout: 3000,
        message: 'Expected block breaking to remain denied outside the mine',
      },
    );
  } finally {
    player.bot.stopDigging();
  }
});
