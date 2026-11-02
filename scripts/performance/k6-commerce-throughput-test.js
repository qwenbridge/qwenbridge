import { configure, runCommerce } from "./commerce-workload.js";
export const options = configure(true);
export default function () { runCommerce(true); }
