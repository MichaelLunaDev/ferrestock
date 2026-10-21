import { createInterface } from 'node:readline';
if (process.env.FERRESTOCK_TEST_PASSWORD || process.env.LOCAL_TEST_ONLY !== 'true') process.exit(1);
const input = createInterface({ input: process.stdin });
input.on('line', line => {
 if (line !== 'STOP_FERRESTOCK_LOCAL') process.exitCode = 1;
 input.close();
});
