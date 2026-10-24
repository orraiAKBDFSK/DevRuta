import { AbstractControl } from '@angular/forms';

/** Clave de traducción del primer error de validación visible de un control, o null si no hay que mostrar nada. */
export function validationKey(control: AbstractControl | null): string | null {
  if (!control || !control.invalid || !(control.touched || control.dirty)) {
    return null;
  }
  const errors = control.errors ?? {};
  for (const key of ['required', 'email', 'minlength', 'maxlength', 'min', 'max']) {
    if (key in errors) {
      return `validation.${key}`;
    }
  }
  return null;
}
