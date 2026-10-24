package app.plataforma.api.catalog.internal;

/** Resumen de una importación. {@code exercises} cuenta los ejercicios de las lecciones nuevas o modificadas. */
record ImportReport(int courses, int units, int skills, int lessonsCreated, int lessonsUpdated, int lessonsUnchanged,
		int lessonsArchived, int lessonsSkippedByStatus, int exercises) {

	@Override
	public String toString() {
		return "cursos=" + courses + ", unidades=" + units + ", habilidades=" + skills + ", lecciones: nuevas="
				+ lessonsCreated + " actualizadas=" + lessonsUpdated + " sin cambios=" + lessonsUnchanged
				+ " archivadas=" + lessonsArchived + " omitidas por estado=" + lessonsSkippedByStatus
				+ ", ejercicios escritos=" + exercises;
	}

}
