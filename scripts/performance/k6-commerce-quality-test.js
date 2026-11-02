import { configure, runCommerce } from "./commerce-workload.js";
export const options = configure(false);
export default function () { runCommerce(false); }
