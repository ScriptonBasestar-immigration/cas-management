/**
 * Created by tschmidt on 2/15/17.
 */
import {Injectable} from '@angular/core';
import {Service} from '../service';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';

@Injectable()
export class FooterService extends Service {

  constructor(protected http: HttpClient) {
    super(http);
  }

  getVersions(): Observable<String[]> {
    return this.get<String[]>('footer');
  }

}
