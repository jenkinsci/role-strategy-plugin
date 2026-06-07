import { useId } from "react";

interface RadioOption<T extends string> {
  value: T;
  label: string;
}

interface RadioGroupProps<T extends string> {
  name: string;
  value: T;
  options: ReadonlyArray<RadioOption<T>>;
  onChange: (next: T) => void;
}

/**
 * Vertical Jenkins-styled radio group using the .jenkins-radio markup that
 * Jenkins core's <f:radio> renders.
 */
export function RadioGroup<T extends string>({
  name,
  value,
  options,
  onChange,
}: RadioGroupProps<T>) {
  const idPrefix = useId();
  return (
    <div className="rsp-radio-stack">
      {options.map((opt) => {
        const id = `${idPrefix}-${opt.value}`;
        return (
          <div key={opt.value} className="jenkins-radio">
            <input
              type="radio"
              className="jenkins-radio__input"
              name={name}
              id={id}
              value={opt.value}
              checked={value === opt.value}
              onChange={() => onChange(opt.value)}
            />
            <label className="jenkins-radio__label" htmlFor={id}>
              {opt.label}
            </label>
          </div>
        );
      })}
    </div>
  );
}
