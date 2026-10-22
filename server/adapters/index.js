import mangadex from './mangadex.js';
import generic from './generic.js';

const adapters = [mangadex, generic];

export const pickAdapter = (url) => adapters.find((a) => a.match(url));
export const adapterNames = () => adapters.filter((a) => a !== generic).map((a) => a.name);
