const LineName = ({ name, hsnSac }) => (
  <>
    {name}
    {hsnSac && (
      <span className="mt-0.5 block text-[13px] font-normal text-spot-muted">
        HSN/SAC {hsnSac}
      </span>
    )}
  </>
);

export default LineName;
