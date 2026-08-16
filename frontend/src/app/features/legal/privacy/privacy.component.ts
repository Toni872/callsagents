import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-privacy',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  template: `
    <div class="legal-page">
      <h1>Política de Privacidad</h1>
      <p class="legal-updated">Última actualización: 14 de agosto de 2026</p>

      <section>
        <h2>1. Responsable del tratamiento</h2>
        <p>
          El responsable del tratamiento de los datos personales recogidos a través de la
          plataforma Callsagents es el proveedor del servicio, que actúa como responsable en
          relación con los datos de las cuentas y del uso de la plataforma, y como encargado en
          relación con los datos de clientes de los usuarios tratados en el marco del servicio.
        </p>
      </section>

      <section>
        <h2>2. Datos que tratamos</h2>
        <p>
          En el contexto del servicio tratamos las siguientes categorías de datos:
        </p>
        <p>
          <strong>Datos de cuenta:</strong> nombre, dirección de correo electrónico y credenciales
          de acceso necesarios para la creación y gestión de la cuenta del usuario.
        </p>
        <p>
          <strong>Datos de llamadas:</strong> registros, metadatos, transcripciones y grabaciones
          de las llamadas automatizadas realizadas a través de la plataforma.
        </p>
        <p>
          <strong>Campañas:</strong> configuración, parámetros y resultados de las campañas de
          llamadas creadas por el usuario.
        </p>
        <p>
          <strong>Leads y citas:</strong> información sobre contactos potenciales, estados de
          prospección y citas agendadas a través del servicio.
        </p>
      </section>

      <section>
        <h2>3. Finalidades del tratamiento</h2>
        <p>
          Los datos se tratan con las siguientes finalidades: prestar y mantener el servicio,
          gestionar las cuentas de los usuarios, ejecutar y monitorizar las campañas de llamadas,
          gestionar leads y citas, garantizar la seguridad del servicio, y cumplir con las
          obligaciones legales aplicables.
        </p>
      </section>

      <section>
        <h2>4. Base legal</h2>
        <p>
          El tratamiento se fundamenta en las siguientes bases legales: el consentimiento del
          interesado, cuando corresponda; la ejecución del contrato de servicio suscrito con el
          usuario; el interés legítimo del responsable en la mejora y seguridad del servicio; y el
          cumplimiento de obligaciones legales.
        </p>
      </section>

      <section>
        <h2>5. Conservación de los datos</h2>
        <p>
          Los datos personales se conservarán durante el tiempo necesario para cumplir con las
          finalidades para las que fueron recogidos y para atender las responsabilidades legales
          derivadas del servicio. Transcurrido dicho plazo, los datos serán suprimidos o anonimizados
          de forma segura, salvo que una norma exija su conservación durante un periodo superior.
        </p>
      </section>

      <section>
        <h2>6. Derechos de los interesados</h2>
        <p>
          De conformidad con el Reglamento (UE) 2016/679 (RGPD), los interesados podrán ejercer en
          cualquier momento los derechos de acceso, rectificación, supresión, limitación del
          tratamiento, portabilidad y oposición, dirigiéndose al responsable por los canales de
          contacto habilitados. Asimismo, podrán presentar una reclamación ante la autoridad de
          control competente.
        </p>
      </section>

      <section>
        <h2>7. Seguridad</h2>
        <p>
          El proveedor aplica medidas técnicas y organizativas adecuadas para proteger los datos
          personales frente a accesos no autorizados, pérdidas, alteraciones o usos indebidos,
          conforme al estado de la técnica y al riesgo del tratamiento. El acceso a los datos queda
          restringido a las personas autorizadas.
        </p>
      </section>

      <section>
        <h2>8. Contacto</h2>
        <p>
          Para cualquier consulta relacionada con el tratamiento de datos personales, los
          interesados pueden dirigirse al responsable a través de los canales de contacto
          disponibles en la plataforma.
        </p>
      </section>
    </div>
  `,
  styles: [
    `
      .legal-page {
        max-width: 760px;
        margin: 0 auto;
        padding: 80px 24px;
        background: var(--color-bg);
        color: var(--color-text);
      }
      .legal-page h1 {
        font-weight: 650;
        letter-spacing: -0.015em;
        margin-bottom: var(--spacing-2);
      }
      .legal-updated {
        color: var(--color-text-muted);
        font-size: 0.875rem;
        margin: 0 0 var(--spacing-6);
      }
      .legal-page h2 {
        font-size: 1.125rem;
        margin-top: var(--spacing-6);
        margin-bottom: var(--spacing-2);
        font-weight: 650;
      }
      .legal-page p {
        line-height: 1.6;
        margin: 0 0 var(--spacing-3);
        color: var(--color-text);
      }
    `
  ]
})
export class PrivacyComponent {}
