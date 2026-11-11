import { createHash, createPublicKey, verify } from 'node:crypto';
import { readFile } from 'node:fs/promises';

const [installerPath, signaturePath, version] = process.argv.slice(2);
const config = JSON.parse(await readFile(new URL('../src-tauri/tauri.windows.conf.json', import.meta.url), 'utf8'));
const publicText = Buffer.from(config.plugins.updater.pubkey, 'base64').toString('utf8');
const publicKey = Buffer.from(publicText.trim().split(/\r?\n/)[1], 'base64');
const signatureText = Buffer.from(await readFile(signaturePath, 'utf8'), 'base64').toString('utf8');
const [, signatureLine, commentLine, globalLine] = signatureText.trim().split(/\r?\n/);
const signature = Buffer.from(signatureLine, 'base64');
const commentPrefix = 'trusted comment: ';

if (publicKey.length !== 42 || publicKey.toString('ascii', 0, 2) !== 'Ed'
    || signature.length !== 74 || signature.toString('ascii', 0, 2) !== 'ED'
    || !signature.subarray(2, 10).equals(publicKey.subarray(2, 10))
    || !commentLine.startsWith(commentPrefix)) {
  throw new Error('The updater signature does not match the pinned public key.');
}

const key = createPublicKey({
  key: { kty: 'OKP', crv: 'Ed25519', x: publicKey.subarray(10).toString('base64url') },
  format: 'jwk',
});
const hash = createHash('blake2b512').update(await readFile(installerPath)).digest();
const installerSignature = signature.subarray(10);
const comment = commentLine.slice(commentPrefix.length);
const signedComment = Buffer.concat([installerSignature, Buffer.from(comment)]);

if (!verify(null, hash, key, installerSignature)
    || !verify(null, signedComment, key, Buffer.from(globalLine, 'base64'))
    || !comment.split('\t').includes(`version:${version}`)) {
  throw new Error('The Windows installer signature or signed version is invalid.');
}
console.log(`Verified the Windows installer signature for ${version}.`);
