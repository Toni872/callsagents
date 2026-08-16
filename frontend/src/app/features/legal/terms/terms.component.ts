import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-terms',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  template: `
    <div class="legal-page">
      <h1>Términos y Condiciones</h1>
      <p class="legal-updated">Última actualización: 14 de agosto de 2026</p>

      <section>
        <h2>1. Aceptación de los términos</h2>
        <p>
          Estos Términos y Condiciones regulan el acceso y uso de la plataforma Callsagents, un
          servicio de automatización de llamadas con IA para la prospección, análisis y gestión de
          clientes. Al acceder o utilizar el servicio, el usuario acepta quedar vinculado por los
          presentes términos. Si no está de acuerdo con ellos, debe abstenerse de utilizar la
          plataforma.
        </p>
      </section>

      <section>
        <h2>2. Descripción del servicio</h2>
        <p>
          Callsagents ofrece una plataforma que permite a los clientes configurar, lanzar y
          monitorizar campañas de llamadas automatizadas asistidas por inteligencia artificial,
          gestionar leads, organizar campañas y coordinar citas. El proveedor del servicio se
          reserva el derecho de modificar, ampliar o reducir las funcionalidades disponibles en
          cualquier momento, con el fin de mejorar la calidad del servicio.
        </p>
      </section>

      <section>
        <h2>3. Cuentas y credenciales</h2>
        <p>
          Para utilizar el servicio es necesario crear una cuenta. El usuario es responsable de
          mantener la confidencialidad de sus credenciales de acceso y de todas las actividades
          que se realicen con ellas. Las credenciales deben ser comunicadas al proveedor de forma
          inmediata en caso de uso no autorizado o sospecha de compromiso.
        </p>
      </section>

      <section>
        <h2>4. Uso aceptable</h2>
        <p>
          El usuario se compromete a utilizar la plataforma de conformidad con la legislación
          vigente, la buena fe y los presentes términos. Queda expresamente prohibido el uso del
          servicio para actividades ilícitas, fraudulentas o contrarias a los derechos de terceros,
          así como cualquier intento de acceder, interferir o alterar el funcionamiento de la
          infraestructura o de las cuentas de otros usuarios.
        </p>
      </section>

      <section>
        <h2>5. Propiedad intelectual</h2>
        <p>
          Todos los contenidos, software, diseños, marcas y elementos gráficos de la plataforma son
          titularidad del proveedor o de sus licenciantes y están protegidos por la normativa de
          propiedad intelectual e industrial. El usuario no adquiere ningún derecho sobre ellos, más
          allá de la licencia limitada y revocable necesaria para utilizar el servicio conforme a
          estos términos.
        </p>
      </section>

      <section>
        <h2>6. Privacidad y protección de datos</h2>
        <p>
          El tratamiento de los datos personales se realiza de conformidad con la legislación
          aplicable en materia de protección de datos y con lo dispuesto en la Política de
          Privacidad, que forma parte integrante de estos términos. El usuario debe disponer de la
          legitimación necesaria para tratar los datos de terceros que introduzca en la plataforma,
          especialmente los relativos a personas contactadas en el marco de campañas de llamadas.
        </p>
      </section>

      <section>
        <h2>7. Limitación de responsabilidad</h2>
        <p>
          El servicio se presta según su disponibilidad, sin garantías de funcionamiento
          ininterrumpido o libre de errores. El proveedor no será responsable de los daños o
          perjuicios derivados del uso del servicio, de la interrupción del mismo, de la pérdida de
          datos o de los resultados obtenidos por el usuario. El usuario es responsable de los
          contenidos y comunicaciones generadas a través de su cuenta.
        </p>
      </section>

      <section>
        <h2>8. Modificaciones y suspensión</h2>
        <p>
          El proveedor podrá modificar los presentes términos en cualquier momento, notificando los
          cambios cuando ello sea posible. Asimismo, podrá suspender o cancelar el acceso al
          servicio en caso de incumplimiento de estos términos o de la normativa aplicable, sin
          perjuicio de las demás acciones legales que pudieran corresponder.
        </p>
      </section>

      <section>
        <h2>9. Legislación aplicable</h2>
        <p>
          Estos términos se rigen por la legislación española. Para cualquier controversia que
          pudiera derivarse de su interpretación o aplicación, las partes se someterán a los
          juzgados y tribunales competentes conforme a la normativa vigente.
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
export class TermsComponent {}
