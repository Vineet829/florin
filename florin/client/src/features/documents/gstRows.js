export const taxRows = (taxes, gst, taxLabel) => {
  if (!gst?.supplyType) {
    return taxes.map((tax) => [`${taxLabel} (${tax.rate}%)`, tax.tax]);
  }
  const payer = gst.reverseCharge ? " (recipient pays)" : "";
  return taxes
    .filter((tax) => Number(tax.rate) > 0)
    .flatMap((tax) => {
      if (gst.supplyType === "INTRA") {
        const half = `${Number(tax.rate) / 2}%`;
        return [
          [`CGST ${half}${payer}`, tax.cgst],
          [
            `${gst.unionTerritory ? "UTGST" : "SGST"} ${half}${payer}`,
            tax.sgst,
          ],
        ];
      }
      return [[`IGST ${tax.rate}%${payer}`, tax.igst]];
    });
};
